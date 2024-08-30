package be.cytomine.api.controller.ontology;

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

import be.cytomine.BasicInstanceBuilder;
import be.cytomine.CytomineCoreApplication;
import be.cytomine.domain.image.group.ImageGroup;
import be.cytomine.domain.ontology.AnnotationGroup;
import be.cytomine.domain.project.Project;
import be.cytomine.service.ontology.AnnotationGroupService;
import be.cytomine.utils.JsonObject;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import javax.transaction.Transactional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CytomineCoreApplication.class)
@AutoConfigureMockMvc
@WithMockUser(username = "superadmin")
public class AnnotationGroupResourceTests {

    @Autowired
    private BasicInstanceBuilder builder;

    @Autowired
    private MockMvc restAnnotationGroupControllerMockMvc;

    @Autowired
    private AnnotationGroupService annotationGroupService;

    @Test
    void find_annotation_group_with_success() {
        AnnotationGroup annotationGroup = builder.given_an_annotation_group();
        AssertionsForClassTypes.assertThat(annotationGroupService.find(annotationGroup.getId()).isPresent());
        assertThat(annotationGroup).isEqualTo(annotationGroupService.find(annotationGroup.getId()).get());
    }

    @Test
    void find_non_existing_annotation_group_return_empty() {
        AssertionsForClassTypes.assertThat(annotationGroupService.find(0L)).isEmpty();
    }

    @Test
    @Transactional
    public void add_valid_annotation_group() throws Exception {
        AnnotationGroup annotationGroup = builder.given_a_not_persisted_annotation_group();
        restAnnotationGroupControllerMockMvc.perform(post("/api/annotationgroup.json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(annotationGroup.toJSON()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printMessage").value(true))
                .andExpect(jsonPath("$.callback").exists())
                .andExpect(jsonPath("$.callback.annotationgroupID").exists())
                .andExpect(jsonPath("$.callback.method").value("be.cytomine.AddAnnotationGroupCommand"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.command").exists())
                .andExpect(jsonPath("$.annotationgroup.id").exists())
                .andExpect(jsonPath("$.annotationgroup.imageGroup").exists());
    }

    @Test
    @Transactional
    public void edit_valid_annotation_group() throws Exception {
        AnnotationGroup annotationGroup = builder.given_an_annotation_group();
        JsonObject jsonObject = annotationGroup.toJsonObject();
        String type = UUID.randomUUID().toString();
        jsonObject.put("type", type);
        restAnnotationGroupControllerMockMvc.perform(put("/api/annotationgroup/{id}.json", annotationGroup.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonObject.toJsonString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printMessage").value(true))
                .andExpect(jsonPath("$.callback").exists())
                .andExpect(jsonPath("$.callback.annotationgroupID").exists())
                .andExpect(jsonPath("$.callback.method").value("be.cytomine.EditAnnotationGroupCommand"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.command").exists())
                .andExpect(jsonPath("$.annotationgroup.id").exists())
                .andExpect(jsonPath("$.annotationgroup.type").value(type));
    }

    @Test
    @Transactional
    public void delete_annotation_group() throws Exception {
        AnnotationGroup annotationGroup = builder.given_an_annotation_group();
        restAnnotationGroupControllerMockMvc.perform(delete("/api/annotationgroup/{id}.json", annotationGroup.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printMessage").value(true))
                .andExpect(jsonPath("$.callback").exists())
                .andExpect(jsonPath("$.callback.annotationgroupID").exists())
                .andExpect(jsonPath("$.callback.method").value("be.cytomine.DeleteAnnotationGroupCommand"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.command").exists())
                .andExpect(jsonPath("$.annotationgroup.id").exists());
    }

    @Test
    @Transactional
    public void list_annotation_group_by_project() throws Exception {
        AnnotationGroup annotationGroup = builder.given_an_annotation_group();
        restAnnotationGroupControllerMockMvc.perform(get("/api/project/{id}/annotationgroup.json", annotationGroup.getProject().getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection[?(@.id==" + annotationGroup.getId() + ")]").exists());
    }

    @Test
    @Transactional
    public void list_annotation_group_by_image_group() throws Exception {
        AnnotationGroup annotationGroup = builder.given_an_annotation_group();
        restAnnotationGroupControllerMockMvc.perform(get("/api/imagegroup/{id}/annotationgroup.json", annotationGroup.getImageGroup().getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection[?(@.id==" + annotationGroup.getId() + ")]").exists());
    }

    @Test
    @Transactional
    public void merge_annotation_group_with_success() throws Exception {
        Project project = builder.given_a_project();
        ImageGroup imageGroup = builder.given_an_imagegroup(project);
        AnnotationGroup annotationGroup = builder.given_an_annotation_group(project, imageGroup);
        AnnotationGroup annotationGroupToMerge = builder.given_an_annotation_group(project, imageGroup);
        restAnnotationGroupControllerMockMvc.perform(post("/api/annotationgroup/{id}/annotationgroup/{mergedId}/merge.json", annotationGroup.getId(), annotationGroupToMerge.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printMessage").value(true))
                .andExpect(jsonPath("$.callback").exists())
                .andExpect(jsonPath("$.callback.annotationgroupID").exists())
                .andExpect(jsonPath("$.callback.method").value("be.cytomine.EditAnnotationGroupCommand"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.command").exists())
                .andExpect(jsonPath("$.annotationgroup.id").exists())
                .andExpect(jsonPath("$.annotationgroup.id").value(annotationGroup.getId()));
    }
}