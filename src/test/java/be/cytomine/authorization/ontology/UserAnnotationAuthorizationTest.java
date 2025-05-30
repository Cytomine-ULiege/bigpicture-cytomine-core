package be.cytomine.authorization.ontology;

/*
 * Copyright (c) 2009-2022. Authors: see NOTICE file.
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

import java.util.Optional;
import java.util.UUID;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.acls.domain.BasePermission;
import org.springframework.security.acls.model.Permission;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import be.cytomine.BasicInstanceBuilder;
import be.cytomine.CytomineCoreApplication;
import be.cytomine.authorization.CRUDAuthorizationTest;
import be.cytomine.domain.ontology.UserAnnotation;
import be.cytomine.domain.project.EditingMode;
import be.cytomine.domain.project.Project;
import be.cytomine.service.ontology.UserAnnotationService;

import static be.cytomine.service.middleware.ImageServerService.IMS_API_BASE_PATH;
import static be.cytomine.service.search.RetrievalService.CBIR_API_BASE_PATH;

@AutoConfigureMockMvc
@SpringBootTest(classes = CytomineCoreApplication.class)
@Transactional
public class UserAnnotationAuthorizationTest extends CRUDAuthorizationTest {

    @Autowired
    private BasicInstanceBuilder builder;

    @Autowired
    private UserAnnotationService userAnnotationService;

    private UserAnnotation userAnnotation = null;

    private static WireMockServer wireMockServer;

    private static void setupStub() {
        /* Simulate call to PIMS */
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching(IMS_API_BASE_PATH + "/image/.*/annotation/drawing"))
            .withRequestBody(WireMock.matching(".*"))
            .willReturn(WireMock.aResponse().withBody(UUID.randomUUID().toString().getBytes()))
        );

        /* Simulate call to CBIR */
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo(CBIR_API_BASE_PATH + "/images"))
            .withQueryParam("storage", WireMock.matching(".*"))
            .withQueryParam("index", WireMock.equalTo("annotation"))
            .willReturn(WireMock.aResponse().withBody(UUID.randomUUID().toString()))
        );

        wireMockServer.stubFor(WireMock.delete(WireMock.urlPathMatching(CBIR_API_BASE_PATH + "/images/.*"))
            .withQueryParam("storage", WireMock.matching(".*"))
            .withQueryParam("index", WireMock.equalTo("annotation"))
            .willReturn(WireMock.aResponse().withBody(UUID.randomUUID().toString()))
        );
    }

    @BeforeAll
    public static void beforeAll() {
        wireMockServer = new WireMockServer(8888);
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        setupStub();
    }

    @AfterAll
    public static void afterAll() {
        wireMockServer.stop();
    }

    @BeforeEach
    public void before() throws Exception {
        if (userAnnotation == null) {
            userAnnotation = builder.given_a_user_annotation();

            initACL(userAnnotation.container());
        }
        userAnnotation.getProject().setMode(EditingMode.CLASSIC);
        builder.persistAndReturn(userAnnotation.getProject());
    }

    @Test
    @WithMockUser(username = SUPERADMIN)
    public void admin_can_list_all_user_Annotations() {
        expectOK(() -> userAnnotationService.listLight());
    }

    @Test
    @WithMockUser(username = USER_ACL_READ)
    public void user_cannot_list_all_user_Annotations() {
        expectForbidden(() -> userAnnotationService.listLight());
    }

    @Test
    @WithMockUser(username = SUPERADMIN)
    public void admin_can_update_annotation_in_restricted_project() {
        UserAnnotation userAnnotation = builder.given_a_user_annotation();
        userAnnotation.setProject(this.userAnnotation.getProject());
        Project project = (Project) userAnnotation.container();
        project.setMode(EditingMode.RESTRICTED);
        builder.persistAndReturn(project);
        expectOK(this::when_i_add_domain);
        expectOK(this::when_i_edit_domain);
        expectOK(this::when_i_delete_domain);
    }

    @Test
    @WithMockUser(username = USER_ACL_READ)
    public void user_cannot_update_annotation_in_restricted_project() {
        UserAnnotation userAnnotation = builder.given_a_user_annotation();
        userAnnotation.setProject(this.userAnnotation.getProject());
        Project project = (Project) userAnnotation.container();
        project.setMode(EditingMode.RESTRICTED);
        builder.persistAndReturn(project);
        expectForbidden(this::when_i_add_domain);
        expectForbidden(this::when_i_edit_domain);
        expectForbidden(this::when_i_delete_domain);

        project.setMode(EditingMode.CLASSIC);
        builder.persistAndReturn(project);
        expectOK(this::when_i_add_domain);
        expectOK(this::when_i_edit_domain);
        expectOK(this::when_i_delete_domain);
    }

    @Test
    @WithMockUser(username = USER_ACL_READ)
    public void user_can_delete_its_annotation_even_if_other_users_has_set_terms() {
        builder.given_an_annotation_term(userAnnotation);
        userAnnotationService.delete(userAnnotation, null, null, false);
    }

    @Override
    public void when_i_get_domain() {
        userAnnotationService.get(userAnnotation.getId());
    }

    @Override
    protected void when_i_add_domain() {
        UserAnnotation annotation = builder.given_a_not_persisted_user_annotation(this.userAnnotation.getProject());
        userAnnotationService.add(annotation.toJsonObject());
    }

    @Override
    public void when_i_edit_domain() {
        userAnnotationService.update(userAnnotation, userAnnotation.toJsonObject());
    }

    @Override
    protected void when_i_delete_domain() {
        UserAnnotation annotation = builder.persistAndReturn(builder.given_a_not_persisted_user_annotation(this.userAnnotation.getProject()));
        userAnnotationService.delete(annotation, null, null, true);
    }

    @Override
    protected Optional<Permission> minimalPermissionForCreate() {
        return Optional.of(BasePermission.READ);
    }

    @Override
    protected Optional<Permission> minimalPermissionForDelete() {
        return Optional.of(BasePermission.READ);
    }

    @Override
    protected Optional<Permission> minimalPermissionForEdit() {
        return Optional.of(BasePermission.READ);
    }

    @Override
    protected Optional<String> minimalRoleForCreate() {
        return Optional.of("ROLE_GUEST");
    }

    @Override
    protected Optional<String> minimalRoleForDelete() {
        return Optional.of("ROLE_GUEST");
    }

    @Override
    protected Optional<String> minimalRoleForEdit() {
        return Optional.of("ROLE_GUEST");
    }
}
