package org.example.mobble.board.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.mobble.board.domain.SearchKey;
import org.example.mobble.board.domain.SearchOrderCase;
import org.example.mobble.report.domain.ReportCase;

public class BoardRequest {
    @Data
    @NoArgsConstructor
    public static class BoardSaveDTO {
        String category;
        String title;
        String content;

        @Builder
        public BoardSaveDTO(String category, String title, String content) {
            this.category = category;
            this.title = title;
            this.content = content;
        }
    }

    @Data
    @NoArgsConstructor
    public static class BoardUpdateDTO {
        Integer id;
        String category;
        String title;
        String content;

        @Builder
        public BoardUpdateDTO(Integer id, String category, String title, String content) {
            this.id = id;
            this.category = category;
            this.title = title;
            this.content = content;
        }
    }

    @Data
    @NoArgsConstructor
    public static class ReportSaveDTO {
        ReportCase result;
        String content;
        String resultEtc;

    }

    @Data
    public static class MyFeedDTO {
        private Integer page = 1;                // 기본값 1
        private String order = "CREATED_AT_ASC"; // 기본값
    }

    @Data
    @NoArgsConstructor
    public static class SearchDTO {
        private String keyword = "";
        private SearchKey key = SearchKey.TITLE_CONTENT;
        private SearchOrderCase order = SearchOrderCase.CREATED_AT_DESC;
        private Integer page = 1;  // 1-base (컨트롤러 내부 로직과 맞춤)
    }
}
