package be.cytomine.api.controller;

import be.cytomine.BasicInstanceBuilder;
import be.cytomine.CytomineCoreApplication;
import be.cytomine.domain.ontology.RelationTerm;
import be.cytomine.domain.ontology.Term;
import be.cytomine.domain.project.Project;
import be.cytomine.repository.image.server.StorageRepository;
import be.cytomine.repository.security.SecUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CytomineCoreApplication.class)
@AutoConfigureMockMvc
@WithMockUser(username = "superadmin")
public class TermResourceTests {

    @Autowired
    private EntityManager em;

    @Autowired
    private BasicInstanceBuilder builder;

    @Autowired
    private MockMvc restTermControllerMockMvc;

    @Test
    @Transactional
    public void list_all_terms() throws Exception {
        Term term = builder.given_a_term();
        restTermControllerMockMvc.perform(get("/api/term"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.collection[?(@.name=='"+term.getName()+"')].ontology").value(term.getOntology().getId().intValue()));
    }


    @Test
    @Transactional
    public void get_a_term() throws Exception {
        Term term = builder.given_a_term();
        Term parent = builder.given_a_term(term.getOntology());
        builder.given_a_relation_term(parent, term);
        em.refresh(term);
        restTermControllerMockMvc.perform(get("/api/term/{id}", term.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(term.getId().intValue()))
                .andExpect(jsonPath("$.class").value("be.cytomine.domain.ontology.Term"))
                .andExpect(jsonPath("$.color").value(term.getColor()))
                .andExpect(jsonPath("$.created").isNotEmpty())
                .andExpect(jsonPath("$.ontology").value(term.getOntology().getId().intValue()))
                .andExpect(jsonPath("$.parent").value(parent.getId().intValue()))
        ;
    }

    @Test
    @Transactional
    public void list_terms_by_ontology() throws Exception {
        Term term = builder.given_a_term();
        restTermControllerMockMvc.perform(get("/api/ontology/{id}/term", term.getOntology().getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.collection[?(@.name=='"+term.getName()+"')].ontology").value(term.getOntology().getId().intValue()));
    }

    @Test
    @Transactional
    public void list_terms_by_project() throws Exception {
        Term term = builder.given_a_term();
        Project project = builder.given_a_project_with_ontology(term.getOntology());
        restTermControllerMockMvc.perform(get("/api/project/{id}/term", project.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.collection[?(@.name=='"+term.getName()+"')].ontology").value(term.getOntology().getId().intValue()));
    }

    @Test
    @Transactional
    public void add_valid_term() throws Exception {
        Term term = BasicInstanceBuilder.given_a_not_persisted_term(builder.given_an_ontology());
        restTermControllerMockMvc.perform(post("/api/term")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(term.toJSON()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printMessage").value(true))
                .andExpect(jsonPath("$.callback").exists())
                .andExpect(jsonPath("$.callback.termID").exists())
                .andExpect(jsonPath("$.callback.method").value("be.cytomine.AddTermCommand"))
                .andExpect(jsonPath("$.callback.ontologyID").value(String.valueOf(term.getOntology().getId())))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.command").exists())
                .andExpect(jsonPath("$.term.id").exists())
                .andExpect(jsonPath("$.term.name").value(term.getName()))
                .andExpect(jsonPath("$.term.ontology").value(term.getOntology().getId()));

// Current response format with Grails:

//        {
//            "printMessage":true,
//                "callback":{
//            "termID":"7034161",
//                    "method":"be.cytomine.AddTermCommand",
//                    "ontologyID":6399460
//        },
//            "term":{
//            "parent":null,
//                    "deleted":null,
//                    "color":"#63d7b6",
//                    "rate":null,
//                    "created":"1634542439860",
//                    "name":"pwet",
//                    "comment":null,
//                    "id":7034161,
//                    "class":"be.cytomine.ontology.Term",
//                    "updated":null,
//                    "ontology":6399460
//        },
//            "message":"Term 7,034,161 (pwet) added in ontology {2}",
//                "command":7034163
//        }



    }

    @Test
    @Transactional
    public void add_term_refused_if_already_exists() throws Exception {
        Term term = BasicInstanceBuilder.given_a_not_persisted_term(builder.given_an_ontology());
        builder.persistAndReturn(term);
        restTermControllerMockMvc.perform(post("/api/term")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(term.toJSON()))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").value("Term " + term.getName() + " already exist in this ontology!"));
    }

    @Test
    @Transactional
    public void add_term_refused_if_ontology_not_set() throws Exception {
        Term term = BasicInstanceBuilder.given_a_not_persisted_term(null);
        restTermControllerMockMvc.perform(post("/api/term")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(term.toJSON()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").value("Ontology is mandatory for term creation"));
    }

    @Test
    @Transactional
    public void add_term_refused_if_name_not_set() throws Exception {
        Term term = BasicInstanceBuilder.given_a_not_persisted_term(builder.given_an_ontology());
        term.setName(null);
        restTermControllerMockMvc.perform(post("/api/term")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(term.toJSON()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Transactional
    public void edit_valid_term() throws Exception {
        Term term = builder.given_a_term();
        restTermControllerMockMvc.perform(put("/api/term/{id}", term.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(term.toJSON()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printMessage").value(true))
                .andExpect(jsonPath("$.callback").exists())
                .andExpect(jsonPath("$.callback.termID").exists())
                .andExpect(jsonPath("$.callback.method").value("be.cytomine.EditTermCommand"))
                .andExpect(jsonPath("$.callback.ontologyID").value(String.valueOf(term.getOntology().getId())))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.command").exists())
                .andExpect(jsonPath("$.term.id").exists())
                .andExpect(jsonPath("$.term.name").value(term.getName()))
                .andExpect(jsonPath("$.term.ontology").value(term.getOntology().getId()));

    }

    @Test
    @Transactional
    public void delete_term() throws Exception {
        Term term = builder.given_a_term();
        restTermControllerMockMvc.perform(delete("/api/term/{id}", term.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(term.toJSON()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printMessage").value(true))
                .andExpect(jsonPath("$.callback").exists())
                .andExpect(jsonPath("$.callback.termID").exists())
                .andExpect(jsonPath("$.callback.method").value("be.cytomine.DeleteTermCommand"))
                .andExpect(jsonPath("$.callback.ontologyID").value(String.valueOf(term.getOntology().getId())))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.command").exists())
                .andExpect(jsonPath("$.term.id").exists())
                .andExpect(jsonPath("$.term.name").value(term.getName()))
                .andExpect(jsonPath("$.term.ontology").value(term.getOntology().getId()));

    }

}
