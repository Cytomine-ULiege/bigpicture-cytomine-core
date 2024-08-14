package be.cytomine.dto.search;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SearchResponse {

    private String query;
    private String storage;
    private String index;
    private List<List<Object>> similarities;
}
