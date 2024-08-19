package be.cytomine.service.search;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import be.cytomine.BasicInstanceBuilder;
import be.cytomine.CytomineCoreApplication;
import be.cytomine.domain.ontology.UserAnnotation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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
        wireMockServer = new WireMockServer(9999);
        wireMockServer.start();
        WireMock.configureFor("localhost", 9999);
    }

    @AfterAll
    public static void afterAll() {
        wireMockServer.stop();
    }

    @Test
    public void delete_index_with_success() {
        UserAnnotation annotation = builder.given_a_user_annotation();

        String expectedUrlPath = "/api/images/" + annotation.getId();

        wireMockServer.stubFor(WireMock.delete(urlPathEqualTo(expectedUrlPath))
            .withQueryParam("storage", WireMock.equalTo(annotation.getProject().getId().toString()))
            .withQueryParam("index", WireMock.equalTo("annotation"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withBody("Success")
            )
        );

        ResponseEntity<String> response = retrievalService.deleteIndex(annotation);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Success", response.getBody());

        wireMockServer.verify(WireMock.deleteRequestedFor(urlPathEqualTo(expectedUrlPath))
            .withQueryParam("storage", WireMock.equalTo(annotation.getProject().getId().toString()))
            .withQueryParam("index", WireMock.equalTo("annotation")));
    }
}
