package org.example.mobble.board.controller;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.example.mobble._util.error.ErrorEnum;
import org.example.mobble._util.error.ex.Exception401;
import org.example.mobble.board.domain.Board;
import org.example.mobble.board.domain.SearchKey;
import org.example.mobble.board.domain.SearchOrderCase;
import org.example.mobble.board.dto.BoardRequest;
import org.example.mobble.board.dto.BoardResponse;
import org.example.mobble.board.service.BoardService;
import org.example.mobble.category.service.CategoryService;
import org.example.mobble.user.domain.User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RequiredArgsConstructor
@Controller
@RequestMapping("/boards")
public class BoardController {
    public static final Integer PER_PAGE = 10;

    private final BoardService boardService;
    private final HttpSession session;
    private final CategoryService categoryService;

    //게시글 저장 페이지 이동
    @GetMapping("/save-form")
    public String boardSaveForm() {
        return "board/save-page";
    }

    @GetMapping("/{id}/update-form")
    public String boardUpdateForm(@PathVariable(name = "id") Integer boardId, HttpServletRequest request) {
        User user = getSessionUser();
        BoardResponse.DetailDTO model = boardService.getUpdateBoardDetail(boardId, user);
        request.setAttribute("model", model);
        return "board/update-page";
    }

    // 모든 게시물 목록 찾기 (검색/정렬/페이징 포함)
    @GetMapping
    public String getBoardsList(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "CREATED_AT_DESC") String order,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "TITLE_CONTENT") String key
    ) {
        User user = getSessionUser();
        var orderCase = safeOrder(order);
        var searchKey = safeKey(key);

        // 검색/정렬/페이징 통합 목록
        List<BoardResponse.DTO> boardDTOList =
                boardService.searchList(
                        user,
                        getFirstIndex(page),
                        PER_PAGE + 1,
                        orderCase,
                        searchKey,
                        keyword
                );

        // 페이지 DTO 만들 때도 keyword/key 포함
        BoardResponse.mainListDTO resDTO = getMainList(
                boardDTOList, page, order, keyword, searchKey.name(), null
        );
        request.setAttribute("model", resDTO);

        // 템플릿에서 선택값 유지용
        request.setAttribute("keyword", keyword);
        request.setAttribute("key", searchKey.name());
        request.setAttribute("order", orderCase.name());
        request.setAttribute("sel_title", searchKey == SearchKey.TITLE_CONTENT);
        request.setAttribute("sel_category", searchKey == SearchKey.CATEGORY);
        request.setAttribute("sel_username", searchKey == SearchKey.USERNAME);
        request.setAttribute("sel_order_created", orderCase == SearchOrderCase.CREATED_AT_DESC);
        request.setAttribute("sel_order_views_desc", orderCase == SearchOrderCase.VIEW_COUNT_DESC);
        request.setAttribute("sel_order_bookmark_desc", orderCase == SearchOrderCase.BOOKMARK_COUNT_DESC);

        return "board/list-page";
    }

    @GetMapping("/{id}")
    public String getBoard(HttpServletRequest request, @PathVariable(name = "id") Integer boardId) {
        User user = getSessionUser();
        BoardResponse.DetailDTO model = boardService.getBoardDetail(boardId, user);
        request.setAttribute("model", model);
        return "board/detail-page";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable(name = "id") Integer boardId, BoardRequest.BoardUpdateDTO reqDTO, HttpServletRequest request) {
        User user = getSessionUser();
        boardService.update(boardId, reqDTO, user);
        return "redirect:/boards/" + boardId;
    }

    // 게시글 저장하기
    @PostMapping
    public String save(BoardRequest.BoardSaveDTO reqDTO) {
        User user = getSessionUser();
        Board board = boardService.save(reqDTO, user);
        return "redirect:/boards/" + board.getId();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable(name = "id") Integer boardId) {
        User user = getSessionUser();
        boardService.delete(boardId, user);
        return "redirect:/boards";
    }


    @PostMapping("/{id}/report")
    public String reportSave(@PathVariable(name = "id") Integer boardId, BoardRequest.ReportSaveDTO reqDTO) {
        User user = getSessionUser();
        boardService.reportSave(user, boardId, reqDTO);
        return "redirect:/boards/" + boardId;
    }


    // 내 피드 목록
    @GetMapping("/me")
    public String getMyFeedList(HttpServletRequest request, BoardRequest.MyFeedDTO reqDTO) {
        User user = getSessionUser();
        List<BoardResponse.DTO> boardDTOList = boardService.getMyFeedList(getFirstIndex(reqDTO.getPage()), PER_PAGE + 1, safeOrder(reqDTO.getOrder()), user);
        // ✅ 기존(4개 인자) 버전은 그대로 사용 가능 (오버로드로 유지)
        BoardResponse.mainListDTO resDTO = getMainList(boardDTOList, reqDTO.getPage(), reqDTO.getOrder(), user);
        request.setAttribute("model", resDTO);
        return "board/myfeed-page";
    }

    // 전역 검색 폼(헤더) → 목록으로 리다이렉트
    @GetMapping("/search")
    public String search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "TITLE_CONTENT") String key,
            @RequestParam(defaultValue = "CREATED_AT_DESC") String order
    ) {
        String q = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        return "redirect:/boards?keyword=" + q + "&key=" + key + "&order=" + order + "&page=1";
    }



    /*                             private logic part
     * ----------------------------------------------------------------------------------
     */

    private SearchOrderCase safeOrder(String order) {
        try {
            return SearchOrderCase.valueOf(order);
        } catch (IllegalArgumentException e) {
            return SearchOrderCase.CREATED_AT_DESC; // 안전 기본값
        }
    }

    private int getFirstIndex(int page) {
        return (normalizePage(page) - 1) * PER_PAGE;
    }

    // 1) 페이지 보정 유틸
    private int normalizePage(Integer page) {
        return (page == null || page < 1) ? 1 : page;
    }

    /**
     * 오버페치 결과(rows)의 개수를 이용해 isFirst / isLast를 계산하고,
     * 화면에는 pageSize개만 잘라서 내려줍니다.
     * request에 page, nextPage, prevPage도 함께 심습니다.
     */

    // ✅ 새 버전: keyword/key 포함
    private BoardResponse.mainListDTO getMainList(
            List<BoardResponse.DTO> boardDTOList,
            Integer page,
            String order,
            String keyword,
            String key,
            User user
    ) {
        User sessionUser = getSessionUser();
        int getSize = 3;
        List<BoardResponse.DTO> popularList = boardService.getPopularList(sessionUser, getSize);
        List<String> categoryList;
        if (user == null) {
            categoryList = categoryService.getPopularList(3);
        } else {
            categoryList = categoryService.getMyFeedPopularList(3, user);
        }
        BoardResponse.mainListDTO.PageDTO pageDTO = getPageDTO(boardDTOList, page, order, keyword, key);
        boardDTOList = !pageDTO.getIsLast() ? boardDTOList.subList(0, PER_PAGE) : boardDTOList;
        return BoardResponse.mainListDTO
                .builder()
                .boardList(boardDTOList)
                .popularList(popularList)
                .categoryList(categoryList)
                .pageDTO(pageDTO)
                .build();
    }

    // ✅ 기존 호환용(예전 4개 인자 호출들이 깨지지 않도록 유지)
    private BoardResponse.mainListDTO getMainList(
            List<BoardResponse.DTO> boardDTOList,
            Integer page,
            String order,
            User user
    ) {
        return getMainList(boardDTOList, page, order, null, null, user);
    }

    // ✅ PageDTO도 오버로드 2개 유지
    private BoardResponse.mainListDTO.PageDTO getPageDTO(
            List<BoardResponse.DTO> boardDTOList,
            Integer page,
            String order,
            String keyword,
            String key
    ) {
        boolean isFirst = page <= 1;
        boolean isLast = boardDTOList.size() <= PER_PAGE;
        return BoardResponse.mainListDTO.PageDTO.builder()
                .isFirst(isFirst)
                .isLast(isLast)
                .page(page)
                .order(safeOrder(order).name())
                .keyword(keyword)
                .key(key)
                .build();
    }

    private BoardResponse.mainListDTO.PageDTO getPageDTO(
            List<BoardResponse.DTO> boardDTOList,
            Integer page,
            String order
    ) {
        return getPageDTO(boardDTOList, page, order, null, null);
    }

    private User getSessionUser() {
        User user = (User) session.getAttribute("user");
        if (user == null) throw new Exception401(ErrorEnum.UNAUTHORIZED_NO_EXISTS_USER_INFO);
        else return user;
    }

    private SearchKey safeKey(String key) {
        try { return SearchKey.valueOf(key); }
        catch (IllegalArgumentException e) { return SearchKey.TITLE_CONTENT; }
    }

}
