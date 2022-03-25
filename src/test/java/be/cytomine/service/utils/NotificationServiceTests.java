package be.cytomine.service.utils;

import be.cytomine.BasicInstanceBuilder;
import be.cytomine.CytomineCoreApplication;
import be.cytomine.config.ApplicationConfiguration;
import be.cytomine.domain.meta.Tag;
import be.cytomine.domain.meta.TagDomainAssociation;
import be.cytomine.domain.security.ForgotPasswordToken;
import be.cytomine.domain.security.User;
import be.cytomine.exceptions.AlreadyExistException;
import be.cytomine.exceptions.WrongArgumentException;
import be.cytomine.repository.meta.TagRepository;
import be.cytomine.repository.ontology.RelationTermRepository;
import be.cytomine.repository.ontology.TermRepository;
import be.cytomine.service.CommandService;
import be.cytomine.service.PermissionService;
import be.cytomine.service.command.TransactionService;
import be.cytomine.service.meta.TagService;
import be.cytomine.service.security.SecurityACLService;
import be.cytomine.utils.CommandResponse;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.thymeleaf.spring5.SpringTemplateEngine;

import javax.mail.MessagingException;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = CytomineCoreApplication.class)
@AutoConfigureMockMvc
@Transactional
public class NotificationServiceTests {

    @Autowired
    BasicInstanceBuilder builder;

    @Autowired
    SpringTemplateEngine springTemplateEngine;


    @Test
    void build_welcome_message() throws IOException, MessagingException {

        CytomineMailService cytomineMailService  = Mockito.mock(CytomineMailService.class);

        ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        applicationConfiguration.setServerURL("http://serverUrlValue");
        applicationConfiguration.setInstanceHostWebsite("http://websiteValue");
        applicationConfiguration.setInstanceHostSupportMail("supportMailValue");
        applicationConfiguration.setInstanceHostPhoneNumber("phoneNumberValue");
        NotificationService notificationService = new NotificationService(cytomineMailService, springTemplateEngine, applicationConfiguration);

        User user = builder.given_default_user();
        ForgotPasswordToken forgotPasswordToken = new ForgotPasswordToken();
        forgotPasswordToken.setTokenKey("123456");
        forgotPasswordToken.setExpiryDate(new Date());

        String content = notificationService.buildNotifyWelcomeMessage(user ,  forgotPasswordToken);

        System.out.println(content);

        assertThat(content).contains("phoneNumberValue");

        // without phone number defined:
        applicationConfiguration.setInstanceHostPhoneNumber("");
        content = notificationService.buildNotifyWelcomeMessage(user ,  forgotPasswordToken);
        System.out.println(content);
        assertThat(content).doesNotContain("phoneNumberValue");

        //Write content to file
        Files.writeString(Paths.get("./", "welcome.html"), content, StandardOpenOption.CREATE);

        notificationService.notifyWelcome(user ,  user, forgotPasswordToken);

        verify(cytomineMailService, times(1)).send(Mockito.any(), Mockito.any(),Mockito.any(),Mockito.any(),Mockito.any(),Mockito.any());
    }

    @Test
    void shared_annotation_message() throws IOException, MessagingException {

        CytomineMailService cytomineMailService  = Mockito.mock(CytomineMailService.class);


        ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        applicationConfiguration.setServerURL("http://serverUrlValue");
        applicationConfiguration.setInstanceHostWebsite("http://websiteValue");
        applicationConfiguration.setInstanceHostSupportMail("supportMailValue");
        applicationConfiguration.setInstanceHostPhoneNumber("phoneNumberValue");
        NotificationService notificationService = new NotificationService(cytomineMailService, springTemplateEngine, applicationConfiguration);

        String content = notificationService.buildNotifyShareAnnotationMessage("fromValue", "commentValue", "http://www.cytomine.com/annotation123", "http://www.cytomine.com/sharedannotation123", "cidValue");

        System.out.println(content);

        //Write content to file
        Files.writeString(Paths.get("./", "share.html"), content, StandardOpenOption.CREATE);

        User user = builder.given_default_user();
        notificationService.notifyShareAnnotationMessage(user , List.of(user.getEmail()), "subjectValue", "fromValue", "commentValue",
                "http://www.cytomine.com/annotation123", "http://www.cytomine.com/sharedannotation123", new HashMap<>(), "cid");

        verify(cytomineMailService, times(1)).send(Mockito.any(), Mockito.any(),Mockito.any(),Mockito.any(),Mockito.any(),Mockito.any(),Mockito.any());
    }


    @Test
    void forgot_username_message() throws IOException, MessagingException {

        CytomineMailService cytomineMailService  = Mockito.mock(CytomineMailService.class);


        ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        applicationConfiguration.setServerURL("http://serverUrlValue");
        applicationConfiguration.setInstanceHostWebsite("http://websiteValue");
        applicationConfiguration.setInstanceHostSupportMail("supportMailValue");
        applicationConfiguration.setInstanceHostPhoneNumber("phoneNumberValue");
        NotificationService notificationService = new NotificationService(cytomineMailService, springTemplateEngine, applicationConfiguration);

        String content = notificationService.buildNotifyForgotUsername("toto");

        assertThat(content).contains("toto");
        //Write content to file
        Files.writeString(Paths.get("./", "forgot_username.html"), content, StandardOpenOption.CREATE);

        User user = builder.given_default_user();
        notificationService.notifyForgotUsername(user);

        verify(cytomineMailService, times(1)).send(
                Mockito.eq(CytomineMailService.NO_REPLY_EMAIL),
                Mockito.eq(new String[]{user.getEmail()}),
                Mockito.any(),
                Mockito.any(),
                Mockito.eq("Cytomine : your username is " + user.getUsername()),
                Mockito.any());
    }

    @Test
    void forgot_password_message() throws IOException, MessagingException {

        CytomineMailService cytomineMailService  = Mockito.mock(CytomineMailService.class);


        ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        applicationConfiguration.setServerURL("http://serverUrlValue");
        applicationConfiguration.setInstanceHostWebsite("http://websiteValue");
        applicationConfiguration.setInstanceHostSupportMail("supportMailValue");
        applicationConfiguration.setInstanceHostPhoneNumber("phoneNumberValue");
        NotificationService notificationService = new NotificationService(cytomineMailService, springTemplateEngine, applicationConfiguration);

        ForgotPasswordToken forgotPasswordToken = new ForgotPasswordToken();
        forgotPasswordToken.setTokenKey("123456");
        forgotPasswordToken.setExpiryDate(new Date());

        String content = notificationService.buildNotifyForgotPassword("toto", forgotPasswordToken);

        assertThat(content).contains("123456");
        //Write content to file
        Files.writeString(Paths.get("./", "forgot_password.html"), content, StandardOpenOption.CREATE);

        User user = builder.given_default_user();
        notificationService.notifyForgotPassword(user, forgotPasswordToken);

        verify(cytomineMailService, times(1)).send(
                Mockito.eq(CytomineMailService.NO_REPLY_EMAIL),
                Mockito.eq(new String[]{user.getEmail()}),
                Mockito.any(),
                Mockito.any(),
                Mockito.eq("Cytomine : reset your password"),
                Mockito.any());
    }
}