package be.cytomine.api.controller.ontology;

import be.cytomine.api.controller.RestCytomineController;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.ontology.OntologyRepository;
import be.cytomine.repository.project.ProjectRepository;
import be.cytomine.service.ontology.OntologyService;
import be.cytomine.service.utils.TaskService;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestOntologyController extends RestCytomineController {

    private final OntologyService ontologyService;

    private final OntologyRepository ontologyRepository;

    private final ProjectRepository projectRepository;

    private final TaskService taskService;

    /**
     * List all ontology visible for the current user
     * For each ontology, print the terms tree
     */
    @GetMapping("/ontology")
    public ResponseEntity<JsonObject> list(
            @RequestParam Map<String,String> allParams
    ) {
        log.debug("REST request to list ontologys");
        boolean light = allParams.containsKey("light") && Boolean.parseBoolean(allParams.get("light"));
        return ResponseEntity.ok(light ? responseList(ontologyService.listLight(),allParams) : response(ontologyService.list(),allParams));
    }

    @GetMapping("/ontology/{id}")
    public ResponseEntity<String> show(
            @PathVariable Long id
    ) {
        log.debug("REST request to get Ontology : {}", id);
        return ontologyService.find(id)
                .map( ontology -> ResponseEntity.ok(convertCytomineDomainToJSON(ontology)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(responseNotFound("Ontology", id).toJsonString()));
    }


    @PostMapping("/ontology")
    public ResponseEntity<String> add(@RequestBody JsonObject json) {
        log.debug("REST request to save Ontology : " + json);
        return add(ontologyService, json);
    }

    @PutMapping("/ontology/{id}")
    public ResponseEntity<String> edit(@PathVariable String id, @RequestBody JsonObject json) {
        log.debug("REST request to edit Ontology : " + id);
        return update(ontologyService, json);
    }

    @DeleteMapping("/ontology/{id}")
    public ResponseEntity<String> delete(@PathVariable String id, @RequestParam(required = false) Long task) {
        log.debug("REST request to delete Ontology : " + id);
        Task existingTask = taskService.get(task);
        return delete(ontologyService, JsonObject.of("id", id), existingTask);
    }

}