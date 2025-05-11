package be.cytomine.controller.search;

import java.io.UnsupportedEncodingException;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import be.cytomine.domain.ontology.AnnotationDomain;
import be.cytomine.dto.image.CropParameter;
import be.cytomine.dto.search.SearchResponse;
import be.cytomine.service.search.RetrievalService;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RetrievalController {

    private final EntityManager entityManager;

    private final RetrievalService retrievalService;

    private CropParameter getParameters(String location) {
        CropParameter parameters = new CropParameter();
        parameters.setComplete(true);
        parameters.setDraw(true);
        parameters.setFormat("png");
        parameters.setIncreaseArea(1.25);
        parameters.setLocation(location);
        parameters.setMaxSize(256);

        return parameters;
    }

    @GetMapping("/retrieval/index")
    public ResponseEntity<String> indexAnnotation(
        @RequestParam(value = "annotation") Long id
    ) throws ParseException, UnsupportedEncodingException {
        log.debug("REST request to index an annotation");

        AnnotationDomain annotation = AnnotationDomain.getAnnotationDomain(entityManager, id);
        CropParameter parameters = getParameters(annotation.getWktLocation());

        return retrievalService.indexAnnotation(annotation, parameters, getRequestETag());
    }

    @GetMapping("/retrieval/search")
    public ResponseEntity<SearchResponse> retrieveSimilarAnnotations(
        @RequestParam(value = "annotation") Long id,
        @RequestParam(value = "nrt_neigh") Long nrt_neigh
    ) throws ParseException, UnsupportedEncodingException {
        log.debug("REST request to retrieve similar annotations given a query annotation {}", id);

        AnnotationDomain annotation = AnnotationDomain.getAnnotationDomain(entityManager, id);
        CropParameter parameters = getParameters(annotation.getWktLocation());

        return retrievalService.retrieveSimilarImages(
            annotation,
            parameters,
            getRequestETag(),
            nrt_neigh
        );
    }
}
