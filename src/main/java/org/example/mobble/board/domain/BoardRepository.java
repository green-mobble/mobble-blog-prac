package org.example.mobble.board.domain;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.mobble.board.dto.BoardResponse;
import org.example.mobble.category.domain.Category;
import org.example.mobble.user.domain.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class BoardRepository {
    private final EntityManager em;

    public Board save(Board board) {
        em.persist(board);
        return board;
    }

    public Optional<Board> findById(Integer boardId) {
        return Optional.ofNullable(em.find(Board.class, boardId));
    }

    public Optional<BoardResponse.DetailDTO> findByIdDetail(Integer boardId, Integer userId) {
        List<Object[]> rows = em.createQuery("""
                        select b, u, c,
                               count(distinct bm),
                               count(distinct bm2) as myCount
                        from Board b
                        left join b.bookmarks bm
                        left join b.bookmarks bm2 on bm2.user.id = :userId and bm2.board.id = b.id
                        left join b.category c
                        left join b.user u
                        where b.id = :boardId
                        group by b, u, c
                        """, Object[].class)
                .setParameter("boardId", boardId)
                .setParameter("userId", userId)
                .getResultList();

        return rows.stream().findFirst().map(result ->
                BoardResponse.DetailDTO.builder()
                        .board((Board) result[0])
                        .user((User) result[1])
                        .category((Category) result[2])
                        .bookmarkCount(((Number) result[3]).intValue())
                        .isBookmark(((Number) result[4]).intValue() > 0)
                        .loginUserId(userId)
                        .build()
        );
    }


    public void delete(Integer boardId) {
        em.remove(em.find(Board.class, boardId));
    }

    public List<BoardResponse.DTO> findAll(Integer userId, String orderBy, Integer firstIndex, Integer maxResult) {
        String jpql = getBaseJpql(null, orderBy);
        return mapping(
                em.createQuery(jpql, Object[].class)
                        .setParameter("userId", userId)
                        .setFirstResult(firstIndex)
                        .setMaxResults(maxResult)
                        .getResultList());
    }

    public List<BoardResponse.DTO> searchSimple(
            Integer loginUserId,
            SearchKey key,
            String keyword,
            String orderBy,
            int firstIndex,
            int maxResult
    ) {
        var joins = new java.util.ArrayList<String>();
        String where = buildSearchWhere(key, keyword, joins, true); // 소문자 비교

        String jpql = getBaseJpql(where, orderBy);
        var q = em.createQuery(String.join(" ", java.util.List.of(
                        // getBaseJpql 내부에서 기본 join 다 붙음
                        // 여긴 추가 join들만 이어붙이기 위함
                        jpql.replace("from Board b",
                                "from Board b" + String.join(" ", joins))
                )), Object[].class)
                .setParameter("userId", loginUserId)
                .setFirstResult(firstIndex)
                .setMaxResults(maxResult);

        bindKeyword(q, keyword);
        return mapping(q.getResultList());
    }

    // 4-2) 북마크 수 정렬(집계 필요) – 이미 group by 걸려 있으니 orderByToString이 count(bm) 주는 방식 유지
    public List<BoardResponse.DTO> searchWithBookmarkOrder(
            Integer loginUserId,
            SearchKey key,
            String keyword,
            String orderBy,
            int firstIndex,
            int maxResult
    ) {
        var joins = new java.util.ArrayList<String>();
        String where = buildSearchWhere(key, keyword, joins, true);

        String jpql = getBaseJpql(where, orderBy);
        jpql = jpql.replace("from Board b",
                "from Board b left join b.bookmarks bm" + String.join(" ", joins)); // count(bm) 집계

        var q = em.createQuery(jpql, Object[].class)
                .setParameter("userId", loginUserId)
                .setFirstResult(firstIndex)
                .setMaxResults(maxResult);

        bindKeyword(q, keyword);
        return mapping(q.getResultList());
    }

    /* ------------------------ private logic part ------------------------ */

    private String getBaseJpql(String whereClause, String orderByClause) {
        String where = (whereClause == null || whereClause.isBlank()) ? "" : whereClause;
        return """
                select b, u, c, count(bm), count(distinct bm2) as myCount
                from Board b
                left join Bookmark bm on bm.board.id = b.id
                left join b.bookmarks bm2 on bm2.user.id = :userId and bm2.board.id = b.id
                left join Category c on c.id = b.category.id
                left join User u on u.id = b.user.id
                """ + where +
                " group by b, u, c " +
                safeOrderBy(orderByClause);
    }

    // orderBy 외부 문자열을 쓸 경우 방어적으로 공백/기본값 보정
    private String safeOrderBy(String orderByClause) {
        if (orderByClause == null || orderByClause.isBlank()) {
            // 기본 정렬(예: 최신순 + 타이브레이커)
            return " order by b.createdAt desc, b.id desc";
        }
        // 반드시 앞에 공백 포함해서 붙이기
        String trimmed = orderByClause.strip();
        return trimmed.toLowerCase().startsWith("order by") ? " " + trimmed : " order by " + trimmed;
    }

    private List<BoardResponse.DTO> mapping(List<Object[]> rows) {
        return rows.stream()
                .map(row -> {
                    Board b = (Board) row[0];
                    User u = (User) row[1];
                    Category c = (Category) row[2];
                    Long cnt = (Long) row[3];
                    Boolean myBookmark = ((Number) row[4]).intValue() > 0;
                    return BoardResponse.DTO.builder()
                            .board(b)
                            .user(u)
                            .category(c)
                            .bookmarkCount(cnt != null ? cnt.intValue() : 0)
                            .isBookmark(myBookmark)
                            .image(null)
                            .build();
                })
                .toList();
    }



    public List<BoardResponse.DTO> findAllByUserId(String orderBy, int firstIndex, int maxResult, User user) {
        String jpql = getBaseJpql(" where u.id = :userId ", orderBy);
        return mapping(
                em.createQuery(jpql, Object[].class)
                        .setParameter("userId", user.getId())
                        .setFirstResult(firstIndex)
                        .setMaxResults(maxResult)
                        .getResultList());
    }

    // 공통: 검색 where 생성
    // ✅ 기존 메서드를 이렇게 바꿔주세요
//  - TITLE_CONTENT: title에는 lower() 적용, content에는 lower() 미적용 (CLOB 때문)
//  - CATEGORY/USERNAME: lower() 계속 사용
    private String buildSearchWhere(SearchKey key, String keyword, List<String> extraJoins, boolean caseInsensitive) {
        if (keyword == null || keyword.isBlank()) return "";

        return switch (key) {
            case TITLE_CONTENT ->
                // title: lower() 사용, content: lower() 쓰지 않음
                    " where (lower(b.title) like :kwLower or b.content like :kwRaw) ";
            case CATEGORY -> {
                extraJoins.add(" join b.category c ");
                yield " where lower(c.category) like :kwLower ";
            }
            case USERNAME -> {
                extraJoins.add(" join b.user u ");
                yield " where lower(u.username) like :kwLower ";
            }
        };
    }


    // 파라미터 바인딩
    private void bindKeyword(jakarta.persistence.Query q, String keyword) {
        if (keyword == null || keyword.isBlank()) return;

        // lower() 비교용
        q.setParameter("kwLower", "%" + keyword.toLowerCase() + "%");
        // CLOB(content) 비교용 - lower() 미사용
        q.setParameter("kwRaw", "%" + keyword + "%");
    }

}
