package be.cytomine.domain.command;

import be.cytomine.domain.CytomineDomain;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.SecUser;
import be.cytomine.domain.security.User;
import be.cytomine.utils.JsonObject;
import lombok.Data;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Entity
@Data
public class CommandHistory extends CytomineDomain {


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "command_id", nullable = true)
    protected Command command;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    protected SecUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = true)
    protected Project project;

    @Column(nullable = true)
    protected String message;

    @Column(nullable = false)
    protected String prefixAction;

    public CommandHistory() {

    }

    public CommandHistory(Command command) {
        this.setCommand(command);
        this.setPrefixAction("");
        this.setProject(command.getProject());
        this.setUser(command.getUser());
        this.setMessage(command.getActionMessage());
    }

    public CommandHistory(UndoStackItem undoStackItem) {
        this.setCommand(undoStackItem.getCommand());
        this.setPrefixAction("UNDO");
        this.setProject(undoStackItem.getCommand().getProject());
        this.setUser(undoStackItem.getUser());
        this.setMessage(undoStackItem.getCommand().getActionMessage());
    }

    public CommandHistory(RedoStackItem redoStackItem) {
        this.setCommand(redoStackItem.getCommand());
        this.setPrefixAction("REDO");
        this.setProject(redoStackItem.getCommand().getProject());
        this.setUser(redoStackItem.getUser());
        this.setMessage(redoStackItem.getCommand().getActionMessage());
    }




    @PrePersist
    public void beforeCreate() {
        super.beforeInsert();
    }

    @PreUpdate
    public void beforeUpdate() {
        super.beforeUpdate();
    }

    public CytomineDomain buildDomainFromJson(JsonObject json, EntityManager entityManager) {
        throw new RuntimeException("Not supported");
    }

    public static JsonObject getDataFromDomain(CytomineDomain domain) {
        JsonObject returnArray = CytomineDomain.getDataFromDomain(domain);
        CommandHistory commandHistory = (CommandHistory)domain;
        returnArray.put("command", commandHistory.getCommand());
        returnArray.put("prefixAction", commandHistory.prefixAction);
        returnArray.put("user", commandHistory.user);
        return returnArray;
    }

    @Override
    public String toJSON() {
        return getDataFromDomain(this).toJsonString();
    }

    @Override
    public JsonObject toJsonObject() {
        return getDataFromDomain(this);
    }
}
