package org.example.mobble.board.domain;

import org.example.mobble.bookmark.domain.BookmarkSortType;

public enum BoardListSortType {
    CREATED_AT_DESC("CREATED_AT_DESC"),
    VIEW_COUNT_DESC("VIEW_COUNT_DESC"),
    BOOKMARK_COUNT_DESC("BOOKMARK_COUNT_DESC");

    private final String value;

    BoardListSortType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    // 문자열을 enum으로 변환하는 헬퍼
    public static BoardListSortType from(String value) {
        for (BoardListSortType type : values()) {
            if (type.getValue().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return CREATED_AT_DESC; // 기본값
    }
}
