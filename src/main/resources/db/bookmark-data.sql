INSERT INTO bookmark_tb (board_id, user_id, created_at)
VALUES (1, 1, CURRENT_TIMESTAMP), -- user1이 board1 북마크
       (2, 1, CURRENT_TIMESTAMP), -- user1이 board2 북마크
       (3, 1, CURRENT_TIMESTAMP),
       (4, 1, CURRENT_TIMESTAMP),
       (5, 1, CURRENT_TIMESTAMP),
       (6, 1, CURRENT_TIMESTAMP),
       (7, 1, CURRENT_TIMESTAMP),
       (8, 1, CURRENT_TIMESTAMP),
       (35, 1, CURRENT_TIMESTAMP),
       (5, 1, CURRENT_TIMESTAMP),
       (1, 2, CURRENT_TIMESTAMP),
       (2, 2, CURRENT_TIMESTAMP); -- user2가 board3 북마크