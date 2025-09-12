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

    public List<Board> searchBoards(String keyword, String orderBy, int firstIndex, int maxResult) {
        String sql = buildSearchQuery(keyword, orderBy);
        
        var query = em.createNativeQuery(sql, Board.class)
                .setFirstResult(firstIndex)
                .setMaxResults(maxResult);
        
        // 키워드에 따른 파라미터 바인딩
        if (keyword != null && !keyword.trim().isEmpty()) {
            String trimmedKeyword = keyword.trim();
            if (trimmedKeyword.startsWith("#")) {
                String category = trimmedKeyword.substring(1);
                query.setParameter("category", category);
            } else if (trimmedKeyword.startsWith("@")) {
                String username = trimmedKeyword.substring(1);
                query.setParameter("username", username);
            } else {
                query.setParameter("keyword", "%" + trimmedKeyword + "%");
            }
        }
        
        @SuppressWarnings("unchecked")
        List<Board> boards = query.getResultList();
        
        return boards;
    }

    public List<Integer> getBookmarkCounts(List<Integer> boardIds) {
        if (boardIds.isEmpty()) {
            return List.of();
        }
        
        String sql = """
                SELECT b.id, COALESCE(COUNT(bm.id), 0) as bookmark_count
                FROM board b
                LEFT JOIN bookmark bm ON b.id = bm.board_id
                WHERE b.id IN :boardIds
                GROUP BY b.id
                ORDER BY b.id
                """;
        
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("boardIds", boardIds)
                .getResultList();
        
        // boardIds 순서대로 북마크 수 매핑
        return boardIds.stream()
                .map(boardId -> rows.stream()
                        .filter(row -> ((Number) row[0]).intValue() == boardId)
                        .findFirst()
                        .map(row -> ((Number) row[1]).intValue())
                        .orElse(0))
                .toList();
    }

    public List<Boolean> getMyBookmarkStatus(List<Integer> boardIds, Integer userId) {
        if (boardIds.isEmpty()) {
            return List.of();
        }
        
        String sql = """
                SELECT board_id
                FROM bookmark
                WHERE board_id IN :boardIds AND user_id = :userId
                """;
        
        @SuppressWarnings("unchecked")
        List<Integer> bookmarkedBoardIds = em.createNativeQuery(sql, Integer.class)
                .setParameter("boardIds", boardIds)
                .setParameter("userId", userId)
                .getResultList();
        
        return boardIds.stream()
                .map(bookmarkedBoardIds::contains)
                .toList();
    }

    private String buildSearchQuery(String keyword, String orderBy) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM board b ");
        
        // 키워드 검색 조건 추가
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append("WHERE ");
            sql.append(buildSearchCondition(keyword.trim()));
        }
        
        // 정렬 조건 추가
        sql.append(" ORDER BY ");
        sql.append(buildOrderByClause(orderBy));
        
        return sql.toString();
    }

    private String buildSearchCondition(String keyword) {
        if (keyword.startsWith("#")) {
            // 카테고리 검색 - 서브쿼리로 카테고리 ID 조회
            return "b.category_id IN (SELECT id FROM category WHERE category = :category)";
        } else if (keyword.startsWith("@")) {
            // 작성자 검색 - 서브쿼리로 사용자 ID 조회
            return "b.user_id IN (SELECT id FROM user WHERE username = :username)";
        } else {
            // 제목과 내용 검색
            return "(b.title LIKE :keyword OR b.content LIKE :keyword)";
        }
    }

    private String buildOrderByClause(String orderBy) {
        if (orderBy == null || orderBy.isBlank()) {
            return "b.created_at DESC, b.id DESC";
        }
        
        String trimmed = orderBy.strip();
        if (trimmed.toLowerCase().startsWith("order by")) {
            trimmed = trimmed.substring(8).trim();
        }
        
        // 정렬 컬럼 매핑
        String orderColumn = switch (trimmed.toLowerCase()) {
            case "views asc" -> "b.views ASC";
            case "views desc" -> "b.views DESC";
            case "bookmark_count asc" -> "b.views ASC"; // 북마크 수는 별도 조회 후 정렬
            case "bookmark_count desc" -> "b.views DESC"; // 북마크 수는 별도 조회 후 정렬
            case "created_at asc" -> "b.created_at ASC";
            case "created_at desc" -> "b.created_at DESC";
            default -> "b.created_at DESC";
        };
        
        return orderColumn + ", b.id DESC";
    }
}
