package be.cytomine.service.search;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.vividsolutions.jts.io.ParseException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import be.cytomine.BasicInstanceBuilder;
import be.cytomine.CytomineCoreApplication;
import be.cytomine.api.controller.ontology.UserAnnotationResourceTests;
import be.cytomine.domain.ontology.UserAnnotation;
import be.cytomine.service.dto.CropParameter;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = CytomineCoreApplication.class)
public class RetrievalServiceTests {

    @Autowired
    private BasicInstanceBuilder builder;

    @Autowired
    private RetrievalService retrievalService;

    private static WireMockServer wireMockServer;

    @BeforeAll
    public static void beforeAll() {
        wireMockServer = new WireMockServer(8888);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8888);
    }

    @AfterAll
    public static void afterAll() {
        wireMockServer.stop();
    }

    @Test
    public void index_annotation_with_success() throws ParseException, UnsupportedEncodingException {
        UserAnnotation annotation = UserAnnotationResourceTests.given_a_user_annotation_with_valid_image_server(builder);

        String id = URLEncoder.encode(annotation.getSlice().getBaseSlice().getPath(), StandardCharsets.UTF_8);
        String url = "/image/" + id + "/annotation/crop";
        String body = "{\"annotations\":{},\"level\":0,\"background_transparency\":0,\"z_slices\":0,\"timepoints\":0}";

        wireMockServer.stubFor(WireMock.post(urlEqualTo(url))
            .withRequestBody(
                WireMock.equalToJson(body)
            )
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withBody(UUID.randomUUID().toString().getBytes())
            )
        );

        String expectedUrlPath = "/api/images";
        String expectedResponseBody = "{ \"ids\": [" + annotation.getId() + "]";
        expectedResponseBody += ", \"storage\": " + annotation.getProject().getId().toString();
        expectedResponseBody += ", \"index\": \"annotation\" }";

        wireMockServer.stubFor(WireMock.post(urlPathEqualTo(expectedUrlPath))
            .withQueryParam("storage", WireMock.equalTo(annotation.getProject().getId().toString()))
            .withQueryParam("index", WireMock.equalTo("annotation"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/json")
                .withBody(expectedResponseBody)
            )
        );

        CropParameter parameters = new CropParameter();
        parameters.setFormat("png");
        ResponseEntity<String> response = retrievalService.indexAnnotation(annotation, parameters, "");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedResponseBody, response.getBody());

        wireMockServer.verify(WireMock.postRequestedFor(urlPathEqualTo(expectedUrlPath))
            .withQueryParam("storage", WireMock.equalTo(annotation.getProject().getId().toString()))
            .withQueryParam("index", WireMock.equalTo("annotation")));
    }

    @Test
    public void delete_index_with_success() {
        UserAnnotation annotation = builder.given_a_user_annotation();

        String expectedUrlPath = "/api/images/" + annotation.getId();
        String expectedResponseBody = "{ \"id\": " + annotation.getId();
        expectedResponseBody += ", \"storage\": " + annotation.getProject().getId().toString();
        expectedResponseBody += ", \"index\": \"annotation\" }";

        wireMockServer.stubFor(WireMock.delete(urlPathEqualTo(expectedUrlPath))
            .withQueryParam("storage", WireMock.equalTo(annotation.getProject().getId().toString()))
            .withQueryParam("index", WireMock.equalTo("annotation"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/json")
                .withBody(expectedResponseBody)
            )
        );

        ResponseEntity<String> response = retrievalService.deleteIndex(annotation);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedResponseBody, response.getBody());

        wireMockServer.verify(WireMock.deleteRequestedFor(urlPathEqualTo(expectedUrlPath))
            .withQueryParam("storage", WireMock.equalTo(annotation.getProject().getId().toString()))
            .withQueryParam("index", WireMock.equalTo("annotation")));
    }
}
