package be.cytomine.service.search;

/*
 * Copyright (c) 2009-2023. Authors: see NOTICE file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import com.vividsolutions.jts.io.ParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import be.cytomine.config.properties.ApplicationProperties;
import be.cytomine.domain.CytomineDomain;
import be.cytomine.domain.ontology.AnnotationDomain;
import be.cytomine.domain.search.RetrievalServer;
import be.cytomine.dto.PimsResponse;
import be.cytomine.service.ModelService;
import be.cytomine.service.dto.CropParameter;
import be.cytomine.service.middleware.ImageServerService;
import be.cytomine.utils.JsonObject;

@Slf4j
@Service
public class RetrievalService extends ModelService {

    private final ImageServerService imageServerService;

    private final HttpClient client;

    private final RestTemplate restTemplate;

    private final String baseUrl;

    public RetrievalService(
        ApplicationProperties applicationProperties,
        ImageServerService imageServerService
    ) {
        this.imageServerService = imageServerService;

        this.client = HttpClient
            .newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

        this.restTemplate = new RestTemplate();
        this.baseUrl = applicationProperties.getRetrievalServerURL();
    }

    @Override
    public Class currentDomain() {
        return RetrievalServer.class;
    }

    @Override
    public CytomineDomain createFromJSON(JsonObject json) {
        return new RetrievalServer().buildDomainFromJson(json, getEntityManager());
    }

    private HttpRequest buildRequest(String url, String filename, byte[] image, byte[] parameters) throws IOException {
        String header = "--data\r\nContent-Disposition: form-data; name=\"image\"; filename=\"" + filename + "\"\r\n\r\n";
        byte[] prefix = header.getBytes();
        byte[] suffix = "\r\n--data--\r\n".getBytes();

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        byteStream.write(parameters);
        byteStream.write(prefix);
        byteStream.write(image);
        byteStream.write(suffix);

        return HttpRequest
            .newBuilder()
            .setHeader("Content-Type", "multipart/form-data; boundary=data")
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofByteArray(byteStream.toByteArray()))
            .build();
    }

    public ResponseEntity<String> indexAnnotation(
        AnnotationDomain annotation,
        CropParameter parameters,
        String etag
    ) throws IOException, ParseException {
        String storageName = annotation.getProject().getId().toString();
        String indexName = "annotation";
        String url = this.baseUrl + "/api/images?storage=" + storageName + "&index=" + indexName;

        // Request annotation crop from PIMS
        PimsResponse crop = imageServerService.crop(annotation.getSlice().getBaseSlice(), parameters, etag);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new ByteArrayResource(crop.getContent()) {
            @Override
            public String getFilename() {
                return annotation.getId().toString();
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        return this.restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
    }

    public void deleteIndex(AnnotationDomain annotation) throws IOException, InterruptedException {
        String url = this.baseUrl + "/api/images/remove?filename=" + annotation.getId();
        HttpRequest request = HttpRequest
            .newBuilder()
            .uri(URI.create(url))
            .DELETE()
            .build();

        HttpResponse<byte[]> response = this.client.send(
            request,
            HttpResponse.BodyHandlers.ofByteArray()
        );

        log.info(String.valueOf(response.statusCode()));
    }

    public Map<String, Object> retrieveSimilarImages(
        AnnotationDomain annotation,
        CropParameter parameters,
        String etag,
        Long nrt_neigh
    ) throws IOException, ParseException, InterruptedException {
        String url = this.baseUrl + "/api/images/retrieve";

        // Request annotation crop from PIMS
        PimsResponse crop = imageServerService.crop(annotation.getSlice().getBaseSlice(), parameters, etag);

        // Format nrt_neigh variable
        byte[] requestParameters = String.format(
            "--data\r\nContent-Disposition: form-data; name=\"nrt_neigh\"\r\n\r\n%s\r\n",
            nrt_neigh
        ).getBytes();

        HttpResponse<byte[]> response = this.client.send(
            buildRequest(url, annotation.getId().toString(), crop.getContent(), requestParameters),
            HttpResponse.BodyHandlers.ofByteArray()
        );

        log.info(String.valueOf(response.statusCode()));

        return JsonObject.toMap(new String(response.body()));
    }
}
