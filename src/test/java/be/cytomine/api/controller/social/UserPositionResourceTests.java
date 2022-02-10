package be.cytomine.api.controller.social;

import be.cytomine.BasicInstanceBuilder;
import be.cytomine.CytomineCoreApplication;
import be.cytomine.domain.image.ImageInstance;
import be.cytomine.domain.image.SliceInstance;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.User;
import be.cytomine.domain.social.LastUserPosition;
import be.cytomine.domain.social.PersistentUserPosition;
import be.cytomine.repositorynosql.social.LastUserPositionRepository;
import be.cytomine.repositorynosql.social.PersistentUserPositionRepository;
import be.cytomine.service.dto.AreaDTO;
import be.cytomine.service.social.UserPositionService;
import be.cytomine.service.social.UserPositionServiceTests;
import be.cytomine.utils.JsonObject;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static be.cytomine.service.social.UserPositionServiceTests.ANOTHER_USER_VIEW;
import static be.cytomine.service.social.UserPositionServiceTests.USER_VIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CytomineCoreApplication.class)
@AutoConfigureMockMvc
@WithMockUser(username = "superadmin")
public class UserPositionResourceTests {

    @Autowired
    private EntityManager em;

    @Autowired
    private BasicInstanceBuilder builder;

    @Autowired
    private MockMvc restUserPositionControllerMockMvc;

    @Autowired
    LastUserPositionRepository lastUserPositionRepository;

    @Autowired
    PersistentUserPositionRepository persistentUserPositionRepository;

    @Autowired
    UserPositionService userPositionService;



    @BeforeEach
    public void cleanDB() {
        lastUserPositionRepository.deleteAll();
        persistentUserPositionRepository.deleteAll();
    }

    PersistentUserPosition given_a_persistent_user_position(Date creation, User user, SliceInstance sliceInstance, boolean broadcast) {
        return given_a_persistent_user_position(creation, user, sliceInstance, UserPositionServiceTests.USER_VIEW, broadcast);
    }

    PersistentUserPosition given_a_persistent_user_position(Date creation, User user, SliceInstance sliceInstance, AreaDTO areaDTO, boolean broadcast) {
        PersistentUserPosition connection =
                userPositionService.add(
                        creation,
                        user,
                        sliceInstance,
                        sliceInstance.getImage(),
                        areaDTO,
                        1,
                        5.0,
                        broadcast
                );
        return connection;
    }

    @Test
    @Transactional
    public void list_last_user_on_image() throws Exception {
        User user = builder.given_a_user();
        SliceInstance sliceInstance = builder.given_a_slice_instance();
        ImageInstance imageInstance = sliceInstance.getImage();

        given_a_persistent_user_position(new Date(), user, sliceInstance, false);

        restUserPositionControllerMockMvc.perform(get("/api/imageinstance/{image}/online.json", imageInstance.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(equalTo(1))))
                .andExpect(jsonPath("$.collection[0]").value(user.getId()));
    }

    @Test
    @Transactional
    public void list_last_broadcast_user_on_image() throws Exception {
        User user = builder.given_a_user();
        SliceInstance sliceInstance = builder.given_a_slice_instance();
        ImageInstance imageInstance = sliceInstance.getImage();

        given_a_persistent_user_position(new Date(), user, sliceInstance, false);

        restUserPositionControllerMockMvc.perform(get("/api/imageinstance/{image}/online.json", imageInstance.getId())
                        .param("broadcast", "true"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(equalTo(0))));

        given_a_persistent_user_position(new Date(), user, sliceInstance, true);

        restUserPositionControllerMockMvc.perform(get("/api/imageinstance/{image}/online.json", imageInstance.getId())
                        .param("broadcast", "true"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(equalTo(1))))
                .andExpect(jsonPath("$.collection[0]").value(user.getId()));
    }

    @Test
    @Transactional
    public void list_user_position() throws Exception {
        User user = builder.given_a_user();
        SliceInstance sliceInstance = builder.given_a_slice_instance();
        ImageInstance imageInstance = sliceInstance.getImage();

        given_a_persistent_user_position(new Date(), user, sliceInstance, false);

        restUserPositionControllerMockMvc.perform(get("/api/imageinstance/{image}/positions.json", imageInstance.getId())
                        .param("showDetails", "true"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(equalTo(1))))
                .andExpect(jsonPath("$.collection[0].user").value(user.getId()));

        restUserPositionControllerMockMvc.perform(get("/api/imageinstance/{image}/positions.json", imageInstance.getId())
                        .param("showDetails", "true")
                        .param("user", user.getId().toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(equalTo(1))))
                .andExpect(jsonPath("$.collection[0].user").value(user.getId()));

        restUserPositionControllerMockMvc.perform(get("/api/imageinstance/{image}/positions.json", imageInstance.getId())
                        .param("showDetails", "true")
                        .param("user", builder.given_a_user().getId().toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(equalTo(0))));
    }


    @Test
    @Transactional
    public void list_after_than() throws Exception {
        User user = builder.given_a_user();
        SliceInstance sliceInstance = builder.given_a_slice_instance();
        ImageInstance imageInstance = sliceInstance.getImage();

        given_a_persistent_user_position(DateUtils.addDays(new Date(), -5), user, sliceInstance, false);

        restUserPositionControllerMockMvc.perform(get("/api/imageinstance/{image}/positions.json", imageInstance.getId())
                        .param("afterThan", String.valueOf(DateUtils.addDays(new Date(), -10).getTime())))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(equalTo(1))));

        restUserPositionControllerMockMvc.perform(get("/api/imageinstance/{image}/positions.json", imageInstance.getId())
                        .param("afterThan", String.valueOf(DateUtils.addDays(new Date(), -3).getTime())))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(equalTo(0))));


    }

    @Test
    @Transactional
    public void list_before_than() throws Exception {
        User user = builder.given_a_user();
        SliceInstance sliceInstance = builder.given_a_slice_instance();
        ImageInstance imageInstance = sliceInstance.getImage();

        given_a_persistent_user_position(DateUtils.addDays(new Date(), -5), user, sliceInstance, false);

        restUserPositionControllerMockMvc.perform(get("/api/imageinstance/{image}/positions.json", imageInstance.getId())
                        .param("beforeThan", String.valueOf(DateUtils.addDays(new Date(), -3).getTime())))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(equalTo(1))));

        restUserPositionControllerMockMvc.perform(get("/api/imageinstance/{image}/positions.json", imageInstance.getId())
                        .param("beforeThan", String.valueOf(DateUtils.addDays(new Date(), -10).getTime())))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(equalTo(0))));
    }

    @Test
    @Transactional
    public void summarize() throws Exception {
        User user = builder.given_a_user();
        SliceInstance sliceInstance = builder.given_a_slice_instance();
        ImageInstance imageInstance = sliceInstance.getImage();

        given_a_persistent_user_position(DateUtils.addMinutes(new Date(), -5), user, sliceInstance, USER_VIEW, false);
        given_a_persistent_user_position(DateUtils.addMinutes(new Date(), -4), user, sliceInstance, USER_VIEW, false);
        given_a_persistent_user_position(DateUtils.addMinutes(new Date(), -3), user, sliceInstance, USER_VIEW, false);
        given_a_persistent_user_position(DateUtils.addMinutes(new Date(), -2), user, sliceInstance, ANOTHER_USER_VIEW, false);


        MvcResult mvcResult = restUserPositionControllerMockMvc.perform(get("/api/imageinstance/{image}/positions.json", imageInstance.getId())
                        .param("user", user.getId().toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(equalTo(2)))).andReturn();
        List<Map<String, Object>> response = (List<Map<String, Object>>)(JsonObject.toMap(mvcResult.getResponse().getContentAsString()).get("collection"));

        Optional<Map<String, Object>> first = response.stream().filter(x -> ((List<List<Double>>) x.get("location")).get(0).contains(USER_VIEW.toList().get(0).get(0))).findFirst();
        assertThat(first).isPresent();
        assertThat(first.get().get("frequency")).isEqualTo(3);
        Optional<Map<String, Object>> second = response.stream().filter(x -> ((List<List<Double>>) x.get("location")).get(0).contains(ANOTHER_USER_VIEW.toList().get(0).get(0))).findFirst();
        assertThat(second).isPresent();
        assertThat(second.get().get("frequency")).isEqualTo(1);

        restUserPositionControllerMockMvc.perform(get("/api/imageinstance/{image}/positions.json", imageInstance.getId())
                        .param("user", builder.given_a_user().getId().toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(equalTo(0))));

    }


    @Test
    @Transactional
    public void add_position() throws Exception {
        User user = builder.given_superadmin();
        SliceInstance sliceInstance = builder.given_a_slice_instance();
        ImageInstance imageInstance = sliceInstance.getImage();

        //{"image":6836067,"zoom":1,"rotation":0,"bottomLeftX":-2344,"bottomLeftY":1032,
        // "bottomRightX":6784,"bottomRightY":1032,"topLeftX":-2344,"topLeftY":2336,"topRightX":6784,"topRightY":2336,"broadcast":false}

        restUserPositionControllerMockMvc.perform(get("/api/imageinstance/{image}/online.json", imageInstance.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(equalTo(0))));

        JsonObject jsonObject = new JsonObject();
        jsonObject.put("image", imageInstance.getId());
        jsonObject.put("zoom", 1);
        jsonObject.put("rotation", 0);
        jsonObject.put("bottomLeftX", -2344);
        jsonObject.put("bottomLeftY", 1032);
        jsonObject.put("bottomRightX", 6784);
        jsonObject.put("bottomRightY", 1032);
        jsonObject.put("topLeftX", -2344);
        jsonObject.put("topLeftY", 2336);
        jsonObject.put("topRightX", 6784);
        jsonObject.put("topRightY", 2336);
        jsonObject.put("broadcast", false);

        restUserPositionControllerMockMvc.perform(post("/api/imageinstance/{image}/position.json", imageInstance.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonObject.toJsonString()))
                .andDo(print())
                .andExpect(status().isOk());

        restUserPositionControllerMockMvc.perform(get("/api/imageinstance/{image}/online.json", imageInstance.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(equalTo(1))))
                .andExpect(jsonPath("$.collection[0]").value(user.getId()));

        List<PersistentUserPosition> persisted = persistentUserPositionRepository.findAll(Sort.by(Sort.Direction.DESC, "created"));
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).getLocation().get(0).get(0)).isEqualTo(-2344);
        assertThat(persisted.get(0).getLocation().get(0).get(1)).isEqualTo(2336);
        assertThat(persisted.get(0).getLocation().get(1).get(0)).isEqualTo(6784);
        assertThat(persisted.get(0).getLocation().get(1).get(1)).isEqualTo(2336);
        assertThat(persisted.get(0).getLocation().get(2).get(0)).isEqualTo(6784);
        assertThat(persisted.get(0).getLocation().get(2).get(1)).isEqualTo(1032);
        assertThat(persisted.get(0).getLocation().get(3).get(0)).isEqualTo(-2344);
        assertThat(persisted.get(0).getLocation().get(3).get(1)).isEqualTo(1032);

        List<LastUserPosition> latest = lastUserPositionRepository.findAll(Sort.by(Sort.Direction.DESC, "created"));
        assertThat(persisted).hasSize(1);
    }



    @Test
    @Transactional
    public void add_position_with_slice_instance() throws Exception {
        User user = builder.given_superadmin();
        SliceInstance sliceInstance = builder.given_a_slice_instance();
        ImageInstance imageInstance = sliceInstance.getImage();

        //{"image":6836067,"zoom":1,"rotation":0,"bottomLeftX":-2344,"bottomLeftY":1032,
        // "bottomRightX":6784,"bottomRightY":1032,"topLeftX":-2344,"topLeftY":2336,"topRightX":6784,"topRightY":2336,"broadcast":false}

        restUserPositionControllerMockMvc.perform(get("/api/sliceinstance/{image}/online.json", sliceInstance.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(equalTo(0))));

        JsonObject jsonObject = new JsonObject();
        jsonObject.put("zoom", 1);
        jsonObject.put("rotation", 0);
        jsonObject.put("bottomLeftX", -2344);
        jsonObject.put("bottomLeftY", 1032);
        jsonObject.put("bottomRightX", 6784);
        jsonObject.put("bottomRightY", 1032);
        jsonObject.put("topLeftX", -2344);
        jsonObject.put("topLeftY", 2336);
        jsonObject.put("topRightX", 6784);
        jsonObject.put("topRightY", 2336);
        jsonObject.put("broadcast", false);

        restUserPositionControllerMockMvc.perform(post("/api/imageinstance/{image}/position.json", imageInstance.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonObject.toJsonString()))
                .andDo(print())
                .andExpect(status().isOk());

        restUserPositionControllerMockMvc.perform(get("/api/sliceinstance/{image}/online.json", sliceInstance.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection", hasSize(equalTo(1))))
                .andExpect(jsonPath("$.collection[0]").value(user.getId()));

        List<PersistentUserPosition> persisted = persistentUserPositionRepository.findAll(Sort.by(Sort.Direction.DESC, "created"));
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).getLocation().get(0).get(0)).isEqualTo(-2344);
        assertThat(persisted.get(0).getLocation().get(0).get(1)).isEqualTo(2336);
        assertThat(persisted.get(0).getLocation().get(1).get(0)).isEqualTo(6784);
        assertThat(persisted.get(0).getLocation().get(1).get(1)).isEqualTo(2336);
        assertThat(persisted.get(0).getLocation().get(2).get(0)).isEqualTo(6784);
        assertThat(persisted.get(0).getLocation().get(2).get(1)).isEqualTo(1032);
        assertThat(persisted.get(0).getLocation().get(3).get(0)).isEqualTo(-2344);
        assertThat(persisted.get(0).getLocation().get(3).get(1)).isEqualTo(1032);

        List<LastUserPosition> latest = lastUserPositionRepository.findAll(Sort.by(Sort.Direction.DESC, "created"));
        assertThat(persisted).hasSize(1);
    }
}