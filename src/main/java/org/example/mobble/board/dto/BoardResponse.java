package org.example.mobble.board.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.mobble.board.domain.Board;
import org.example.mobble.category.domain.Category;
import org.example.mobble.report.domain.Report;
import org.example.mobble.report.domain.ReportCase;
import org.example.mobble.user.domain.User;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class BoardResponse {
    @Data
    public static class mainListDTO {
        List<DTO> boardList;
        List<DTO> popularList;
        List<String> categoryList;
        PageDTO pageDTO;

        @Data
        public static class PageDTO {
            Integer page;
            Boolean isFirst;
            Boolean isLast;
            Integer prev;
            Integer next;
            String queryString;

            @Builder
            public PageDTO(Integer page, Boolean isFirst, Boolean isLast, String order) {
                this.page = page;
                this.isFirst = isFirst;
                this.isLast = isLast;
                this.prev = !isFirst ? page - 1 : page;
                this.next = !isLast ? page + 1 : page;
                this.queryString = "?order=" + order;
            }
        }

        @Builder
        public mainListDTO(List<DTO> boardList, List<DTO> popularList, List<String> categoryList, PageDTO pageDTO) {
            this.boardList = boardList;
            this.popularList = popularList;
            this.categoryList = categoryList;
            this.pageDTO = pageDTO;
        }
    }

    // 팀원 간 컨벤션 토의
    @Data
    public static class DTO {
        Integer id;
        String username;
        String title;
        String content;
        Integer views;
        Integer bookmarkCount;
        Boolean isBookmark;
        String category;
        String createAt;
        String updateAt;
        String image;

        @Builder
        public DTO(Board board, User user, Category category, Integer bookmarkCount, String image, Boolean isBookmark) {
            this.id = board.getId();
            this.username = user.getUsername();
            this.title = board.getTitle();
            this.content = board.getContent();
            this.views = board.getViews();
            this.bookmarkCount = bookmarkCount;
            this.isBookmark = isBookmark;
            this.category = (category != null) ? category.getCategory() : null;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            Timestamp updatedAt = board.getUpdatedAt();
            this.createAt = board.getCreatedAt().toLocalDateTime().format(formatter);
            if (updatedAt != null) {
                this.updateAt = updatedAt.toLocalDateTime().format(formatter);
            }
            this.image = image;
        }
    }

    @Data
    public static class DetailDTO {
        Integer id;
        String username;
        String title;
        String content;
        Integer views;
        Integer bookmarkCount;
        String category;
        Timestamp createAt;
        Timestamp updateAt;
        String profileImage;
        Boolean isBookmark;
        Boolean isOwner;

        // yyyy-mm-dd 변경 + 최종 표시할 날짜 (생성 or 수정)
        // 수정 일이 있으면 수정 일자로 표기
        String displayDate;

        @Builder
        public DetailDTO(Board board, User user, Category category, Integer bookmarkCount, Boolean isBookmark, Integer loginUserId) {
            this.id = board.getId();
            this.username = user.getUsername();
            this.title = board.getTitle();
            this.content = board.getContent();
            this.views = board.getViews();
            this.bookmarkCount = bookmarkCount;
            this.category = category.getCategory();
            this.createAt = board.getCreatedAt();
            this.updateAt = board.getUpdatedAt();
            this.profileImage = user.getProfileImage();
            this.isBookmark = isBookmark;
            //현재 User는 board 작성자의 유저 정보이기때문에 로그인한 유저의 아이디를 새로 받아서 확인 로직을 구현 했음
            this.isOwner = board.getUser().getId().equals(loginUserId);

            // ---- 날짜 처리 로직 ----
            Timestamp base = (board.getUpdatedAt() != null) ? board.getUpdatedAt() : board.getCreatedAt();
            this.displayDate = base.toLocalDateTime().toLocalDate().toString(); // yyyy-MM-dd

            // ===== DEBUG LOG =====
            System.out.println("✅ Board.id = " + board.getId());
            System.out.println("✅ Board.title = " + board.getTitle());
            System.out.println("✅ Board.user.id = " + board.getUser().getId());
            System.out.println("✅ Board.user.username = " + board.getUser().getUsername());
            System.out.println("✅ User.username = " +  user.getUsername());
            System.out.println("✅ LoginUserId = " + loginUserId);
        }
    }

    @Data
    @NoArgsConstructor
    public static class ReportSaveDTO {
        Integer id;
        ReportCase result;
        String content;
        String resultEtc;
        Integer userId;
        Integer boardId;

        public ReportSaveDTO(Report report) {
            this.id = report.getId();
            this.result = report.getResult();
            this.content = report.getContent();
            this.resultEtc = report.getResultEtc();
            this.userId = report.getUser().getId();
            this.boardId = report.getBoard().getId();
        }
    }
}
