package be.cytomine.api.controller;

import be.cytomine.domain.command.Command;
import be.cytomine.domain.command.RedoStackItem;
import be.cytomine.domain.command.UndoStackItem;
import be.cytomine.domain.security.SecUser;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.command.CommandRepository;
import be.cytomine.repository.command.UndoStackItemRepository;
import be.cytomine.repository.ontology.OntologyRepository;
import be.cytomine.repository.project.ProjectRepository;
import be.cytomine.service.CommandService;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.ontology.OntologyService;
import be.cytomine.service.security.SecUserService;
import be.cytomine.service.utils.TaskService;
import be.cytomine.utils.CommandResponse;
import be.cytomine.utils.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("")
@Slf4j
@RequiredArgsConstructor
public class CommandController extends RestCytomineController {

    private final CurrentUserService currentUserService;

    private final CommandRepository commandRepository;

    private final MessageSource messageSource;

    private final CommandService commandService;


    @GetMapping({"/command/undo.json", "/command/{id}/undo.json"})
    public ResponseEntity<String> undo(
            @PathVariable(required = false) Long id
    ) {
        log.debug("REST request to undo command {}", id);
        SecUser user = currentUserService.getCurrentUser();
        Command command = null;
        if (id!=null) {
            command = commandRepository.findById(id)
                    .orElseThrow(() -> new ObjectNotFoundException("Command" , id));
        }


        //Get the last command list with max 1 command

        Optional<UndoStackItem> lastCommand;
        if (command!=null) {
            lastCommand = commandRepository.findLastUndoStackItem(user, command);
        } else {
            lastCommand = commandRepository.findLastUndoStackItem(user);
        }

        //There is no command, so nothing to undo
        if (lastCommand.isEmpty()) {
            String message = messageSource.getMessage("be.cytomine.UndoCommand", new Object[0], Locale.ENGLISH);
            return responseSuccess(List.of(JsonObject.of("success", true, "message", message, "callback", null, "printMessage", true)));
        }

        //Last command done
        UndoStackItem firstUndoStack = lastCommand.get();
        List<CommandResponse> results = commandService.undo(firstUndoStack, user);


        if(results.stream().anyMatch(x -> x.getStatus()!=200 && x.getStatus()!=201)) {
            response.setStatus(400);
        } else {
            response.setStatus(200);
        }
        return responseSuccess(results);
    }


    @GetMapping({"/command/redo.json", "/command/{id}/redo.json"})
    public ResponseEntity<String> redo(
            @PathVariable(required = false) Long id
    ) {
        log.debug("REST request to undo command {}", id);
        SecUser user = currentUserService.getCurrentUser();
        Command command = null;
        if (id!=null) {
            command = commandRepository.findById(id)
                    .orElseThrow(() -> new ObjectNotFoundException("Command" , id));
        }


        //Get the last command list with max 1 command

        Optional<RedoStackItem> lastCommand;
        if (command!=null) {
            lastCommand = commandRepository.findLastRedoStackItem(user, command);
        } else {
            lastCommand = commandRepository.findLastRedoStackItem(user);
        }

        //There is no command, so nothing to undo
        if (lastCommand.isEmpty()) {
            String message = messageSource.getMessage("be.cytomine.'be.cytomine.RedoCommand'", new Object[0], Locale.ENGLISH);
            return responseSuccess(List.of(JsonObject.of("success", true, "message", message, "callback", null, "printMessage", true)));
        }

        //Last command done
        RedoStackItem firstRedoStack = lastCommand.get();
        List<CommandResponse> results = commandService.redo(firstRedoStack, user);


        if(results.stream().anyMatch(x -> x.getStatus()!=200 && x.getStatus()!=201)) {
            response.setStatus(400);
        } else {
            response.setStatus(200);
        }
        return responseSuccess(results);
    }

}