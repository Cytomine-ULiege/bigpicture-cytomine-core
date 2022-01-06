package be.cytomine.service.ontology;

import be.cytomine.domain.CytomineDomain;
import be.cytomine.domain.command.*;
import be.cytomine.domain.image.ImageInstance;
import be.cytomine.domain.image.SliceInstance;
import be.cytomine.domain.ontology.AnnotationDomain;
import be.cytomine.domain.ontology.AnnotationTerm;
import be.cytomine.domain.ontology.Term;
import be.cytomine.domain.ontology.AlgoAnnotation;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.SecUser;
import be.cytomine.domain.security.User;
import be.cytomine.dto.AnnotationLight;
import be.cytomine.dto.SimplifiedAnnotation;
import be.cytomine.exceptions.CytomineMethodNotYetImplementedException;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.exceptions.WrongArgumentException;
import be.cytomine.repository.AlgoAnnotationListing;
import be.cytomine.repository.ontology.AlgoAnnotationRepository;
import be.cytomine.service.AnnotationListingService;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.ModelService;
import be.cytomine.service.command.TransactionService;
import be.cytomine.service.dto.BoundariesCropParameter;
import be.cytomine.service.image.ImageInstanceService;
import be.cytomine.service.image.SliceInstanceService;
import be.cytomine.service.security.SecurityACLService;
import be.cytomine.service.utils.SimplifyGeometryService;
import be.cytomine.service.utils.ValidateGeometryService;
import be.cytomine.utils.CommandResponse;
import be.cytomine.utils.GeometryUtils;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.Task;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.*;

import static org.springframework.security.acls.domain.BasePermission.DELETE;
import static org.springframework.security.acls.domain.BasePermission.READ;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AlgoAnnotationService extends ModelService {

    private final AlgoAnnotationRepository algoAnnotationRepository;

    private final SecurityACLService securityACLService;

    private final CurrentUserService currentUserService;

    private final AnnotationListingService annotationListingService;

    private final EntityManager entityManager;

    private final SliceInstanceService sliceInstanceService;

    private final ImageInstanceService imageInstanceService;

    private final SimplifyGeometryService simplifyGeometryService;

    private final TransactionService transactionService;

    private final ValidateGeometryService validateGeometryService;

    private final AlgoAnnotationTermService algoAnnotationTermService;

    @Override
    public Class currentDomain() {
        return AlgoAnnotation.class;
    }

    @Override
    public CytomineDomain createFromJSON(JsonObject json) {
        return new AlgoAnnotation().buildDomainFromJson(json, entityManager);
    }

    public AlgoAnnotation get(Long id) {
        return find(id).orElse(null);
    }

    public Optional<AlgoAnnotation> find(Long id) {
        Optional<AlgoAnnotation> optionalAlgoAnnotation = algoAnnotationRepository.findById(id);
        optionalAlgoAnnotation.ifPresent(algoAnnotation -> securityACLService.check(algoAnnotation.container(),READ));
        return optionalAlgoAnnotation;
    }

    public Long countByProject(Project project) {
        return countByProject(project, null, null);
    }

    public Long countByProject(Project project, Date startDate, Date endDate) {
        if (startDate!=null && endDate!=null) {
            return algoAnnotationRepository.countByProjectAndCreatedBetween(project, startDate, endDate);
        } else if (startDate!=null) {
            return algoAnnotationRepository.countByProjectAndCreatedAfter(project, startDate);
        } else if (endDate!=null) {
            return algoAnnotationRepository.countByProjectAndCreatedBefore(project, endDate);
        } else {
            return algoAnnotationRepository.countByProject(project);
        }
    }


    public List list(Project project, List<String> propertiesToShow) {
        securityACLService.check(project,READ);
        AlgoAnnotationListing algoAnnotationListing = new AlgoAnnotationListing(entityManager);
        algoAnnotationListing.setColumnsToPrint(propertiesToShow);
        algoAnnotationListing.setProject(project.getId());
        return annotationListingService.executeRequest(algoAnnotationListing);
    }

    //TODO:
//    public List list(Job job, List<String> propertiesToShow) {
//        securityACLService.check(project,READ);
//        AlgoAnnotationListing algoAnnotationListing = new AlgoAnnotationListing(entityManager);
//        algoAnnotationListing.setColumnsToPrint(propertiesToShow);
//        algoAnnotationListing.setProject(project.getId());
//        return annotationListingService.executeRequest(algoAnnotationListing);
//    }

    public List listIncluded(ImageInstance image, String geometry, SecUser user, List<Long> terms, AnnotationDomain annotation, List<String> propertiesToShow) {
        securityACLService.check(image.container(), READ);
        AlgoAnnotationListing algoAnnotationListing = new AlgoAnnotationListing(entityManager);
        algoAnnotationListing.setColumnsToPrint(propertiesToShow);
        algoAnnotationListing.setImage(image.getId());
        algoAnnotationListing.setUser(user.getId());
        algoAnnotationListing.setTerms(terms);
        algoAnnotationListing.setExcludedAnnotation((annotation!=null? annotation.getId() : null));
        algoAnnotationListing.setBbox(geometry);
        return annotationListingService.executeRequest(algoAnnotationListing);
    }

    /**
     * Add the new domain with JSON data
     * @param jsonObject New domain data
     * @return Response structure (created domain data,..)
     */
    @Override
    public CommandResponse add(JsonObject jsonObject) {
        SliceInstance slice = null;
        ImageInstance image = null;

        if (!jsonObject.isMissing("slice")) {
            slice = sliceInstanceService.find(jsonObject.getJSONAttrLong("slice"))
                    .orElseThrow(() -> new ObjectNotFoundException("SliceInstance with id " + jsonObject.get("slice")));
            image = slice.getImage();
        } else if (!jsonObject.isMissing("image")) {
            image = imageInstanceService.find(jsonObject.getJSONAttrLong("image"))
                    .orElseThrow(() -> new ObjectNotFoundException("ImageInstance with id " + jsonObject.get("image")));
            slice = imageInstanceService.getReferenceSlice(image);

        }
        Project project = slice.getProject();

        if (jsonObject.isMissing("location")) {
            throw new WrongArgumentException("Annotation must have a valid geometry:" + jsonObject.get("location"));
        }

        jsonObject.put("slice", slice.getId());
        jsonObject.put("image", image.getId());
        jsonObject.put("project", project.getId());

        securityACLService.check(project, READ);
        securityACLService.checkIsNotReadOnly(project);

        SecUser currentUser = currentUserService.getCurrentUser();
        if (!currentUser.isAlgo()) {
            throw new WrongArgumentException("user "+currentUser+" is not an userjob");
        }
        jsonObject.put("user", currentUser.getId());



        Geometry annotationShape;
        try {
            annotationShape = new WKTReader().read(jsonObject.getJSONAttrStr("location"));
        }
        catch (Exception ignored) {
            throw new WrongArgumentException("Annotation location is not valid");
        }

        if (!annotationShape.isValid()) {
            throw new WrongArgumentException("Annotation location is not valid");
        }


        Envelope envelope = annotationShape.getEnvelopeInternal();
        boolean isSizeDefined = image.getBaseImage().getWidth()!=null && image.getBaseImage().getHeight()!=null;
        if (isSizeDefined && (envelope.getMinX() < 0 || envelope.getMinY() < 0 ||
                envelope.getMaxX() > image.getBaseImage().getWidth() ||
                envelope.getMaxY() > image.getBaseImage().getHeight())) {
            double maxX = Math.min(envelope.getMaxX(), image.getBaseImage().getWidth());
            double maxY = Math.min(envelope.getMaxY(), image.getBaseImage().getHeight());
            Geometry insideBounds = null;
            try {
                insideBounds = new WKTReader().read("POLYGON((0 0,0 " + maxY + "," + maxX + " " + maxY + "," + maxX + " 0,0 0))");
            } catch (ParseException e) {
                throw new WrongArgumentException("Annotation cannot be parsed with maxX/maxY:" + e.getMessage());
            }
            annotationShape = annotationShape.intersection(insideBounds);
        }

        if(!(annotationShape.getGeometryType().equals("LineString"))) {
            BoundariesCropParameter boundaries = GeometryUtils.getGeometryBoundaries(annotationShape);
            if (boundaries == null || boundaries.getWidth() == 0 || boundaries.getHeight() == 0) {
                throw new WrongArgumentException("Annotation dimension not valid");
            }
        }

        //simplify annotation
        try {
            SimplifiedAnnotation simplifiedAnnotation =
                    simplifyGeometryService.simplifyPolygon(annotationShape, jsonObject.getJSONAttrLong("minPoint", null), jsonObject.getJSONAttrLong("maxPoint", null));
            jsonObject.put("location", simplifiedAnnotation.getNewAnnotation());
            jsonObject.put("geometryCompression", simplifiedAnnotation.getRate());
        } catch (Exception e) {
            log.error("Cannot simplify annotation location:" + e);
        }

        if (jsonObject.isMissing("location")) {
            jsonObject.put("location", annotationShape);
            jsonObject.put("geometryCompression", 0.0d);
        }

        if (jsonObject.get("location") instanceof Geometry) {
            jsonObject.put("location", validateGeometryService.tryToMakeItValidIfNotValid((Geometry)jsonObject.get("location")));
        } else {
            jsonObject.put("location", validateGeometryService.tryToMakeItValidIfNotValid(jsonObject.getJSONAttrStr("location")));
        }

        //Start transaction
        Transaction transaction = transactionService.start();

        CommandResponse commandResponse = executeCommand(new AddCommand(currentUser, transaction), null, jsonObject);
        AlgoAnnotation addedAnnotation = (AlgoAnnotation)commandResponse.getObject();

        if (addedAnnotation == null) {
            return commandResponse;
        }


        // Add annotation-term if any
        List<Long> termIds = new ArrayList<>();
        termIds.addAll(jsonObject.getJSONAttrListLong("term", new ArrayList<>()));
        termIds.addAll(jsonObject.getJSONAttrListLong("terms", new ArrayList<>()));

        List<Term> terms = new ArrayList<>();
        for (Long termId : termIds) {

            CommandResponse response = algoAnnotationTermService.addAlgoAnnotationTerm(addedAnnotation, termId, currentUser.getId(), currentUser, transaction);
            terms.add(((AnnotationTerm)(response.getObject())).getTerm());
        }

        ((Map<String, Object>)commandResponse.getData().get("annotation")).put("term", terms);


        // Add properties if any
        //TODO: uncomment when properties will be implemented
        Map<String, String> properties = new HashMap<>();
        properties.putAll(jsonObject.getJSONAttrMapString("property", new HashMap<>()));
        properties.putAll(jsonObject.getJSONAttrMapString("properties", new HashMap<>()));

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            throw new CytomineMethodNotYetImplementedException("");
//            String key = entry.getKey();
//            String value = entry.getValue();
//            propertyService.addProperty("be.cytomine.ontology.AlgoAnnotation", addedAnnotation.getId(), key, value, currentUser, transaction);
        }

        //TODO: uncomment when track will be implemented
        // Add annotation-term if any
        List<Long> tracksIds = new ArrayList<>();
        tracksIds.addAll(jsonObject.getJSONAttrListLong("track", new ArrayList<>()));
        tracksIds.addAll(jsonObject.getJSONAttrListLong("tracks", new ArrayList<>()));
        if (!tracksIds.isEmpty()) {
            throw new CytomineMethodNotYetImplementedException("");
        }
//        List<AnnotationTrack> annotationTracks = new ArrayList<>();
//        for (Long trackId : tracksIds) {
//            CommandResponse response =
//                    annotationTrackService.addAnnotationTrack("be.cytomine.ontology.AlgoAnnotation", addedAnnotation.getId(), trackId, addedAnnotation.getSlice().getId(), currentUser, transaction);
//            annotationTracks.add((AnnotationTrack) response.getData().get("annotationtrack"));
//        }
//        ((Map<String, Object>)commandResponse.getData().get("annotation")).put("annotationTrack", annotationTracks);
//        ((Map<String, Object>)commandResponse.getData().get("annotation")).put("track", annotationTracks.stream().map(x -> x.getTrack()).collect(Collectors.toList()));



        return commandResponse;
    }

    protected void beforeAdd(CytomineDomain domain) {
        // this will be done in the PrePersist method ; but the validation is done before PrePersist
        ((AlgoAnnotation)domain).setWktLocation(((AlgoAnnotation)domain).getLocation().toText());
    }

    protected void afterAdd(CytomineDomain domain, CommandResponse response) {
        response.getData().put("annotation", response.getData().get("algoannotation"));
        response.getData().remove("algoannotation");
    }


    /**
     * Update this domain with new data from json
     * @param domain Domain to update
     * @param jsonNewData New domain datas
     * @return Response structure (new domain data, old domain data..)
     */
    public CommandResponse update(CytomineDomain domain, JsonObject jsonNewData, Transaction transaction) {
        SecUser currentUser = currentUserService.getCurrentUser();
        //securityACLService.checkIsSameUserOrAdminContainer(annotation,annotation.user,currentUser)
        securityACLService.checkFullOrRestrictedForOwner(domain, ((AlgoAnnotation)domain).getUser());

        // TODO: what about image/project ??

        Geometry annotationShape;
        try {
            annotationShape = new WKTReader().read(jsonNewData.getJSONAttrStr("location"));
        }
        catch (ParseException ignored) {
            throw new WrongArgumentException("Annotation location is not valid");
        }

        if (!annotationShape.isValid()) {
            throw new WrongArgumentException("Annotation location is not valid");
        }

        //simplify annotation
        try {
            double rate = jsonNewData.getJSONAttrDouble("geometryCompression", 0d);
            SimplifiedAnnotation data = simplifyGeometryService.simplifyPolygon(annotationShape, rate);
            jsonNewData.put("location", data.getNewAnnotation());
            jsonNewData.put("geometryCompression", data.getRate());
        } catch (Exception e) {
            log.error("Cannot simplify annotation location:" + e);
        }

        CommandResponse result = executeCommand(new EditCommand(currentUser, null), domain, jsonNewData);
        return result;
    }


    protected void afterUpdate(CytomineDomain domain, CommandResponse response) {
        response.getData().put("annotation", response.getData().get("algoannotation"));
        response.getData().remove("algoannotation");
    }



    /**
     * Delete this domain
     * @param domain Domain to delete
     * @param transaction Transaction link with this command
     * @param task Task for this command
     * @param printMessage Flag if client will print or not confirm message
     * @return Response structure (code, old domain,..)
     */
    @Override
    public CommandResponse delete(CytomineDomain domain, Transaction transaction, Task task, boolean printMessage) {
        SecUser currentUser = currentUserService.getCurrentUser();
        securityACLService.check(domain.container(),DELETE);
        Command c = new DeleteCommand(currentUser, transaction);
        return executeCommand(c,domain, null);
    }

    @Override
    public void checkDoNotAlreadyExist(CytomineDomain domain) {

    }

    protected void afterDelete(CytomineDomain domain, CommandResponse response) {
        response.getData().put("annotation", response.getData().get("algoannotation"));
        response.getData().remove("algoannotation");
    }


    public List<Object> getStringParamsI18n(CytomineDomain domain) {
        AlgoAnnotation annotation = (AlgoAnnotation)domain;
        return List.of(currentUserService.getCurrentUser().toString(), annotation.getImage().getBlindInstanceFilename(), ((AlgoAnnotation) domain).getUser().toString());
    }

    public void deleteDependencies(CytomineDomain domain, Transaction transaction, Task task) {
        return;
    }

    //TODO
//
//    def deleteDependentAlgoAnnotationTerm(AlgoAnnotation ao, Transaction transaction, Task task = null) {
//        AlgoAnnotationTerm.findAllByAnnotationIdent(ao.id).each {
//            algoAnnotationTermService.delete(it,transaction,null,false)
//        }
//    }
//
//    def deleteDependentReviewedAnnotation(AlgoAnnotation aa, Transaction transaction, Task task = null) {
//        ReviewedAnnotation.findAllByParentIdent(aa.id).each {
//            reviewedAnnotationService.delete(it,transaction,null,false)
//        }
//    }
//
//    def deleteDependentSharedAnnotation(AlgoAnnotation aa, Transaction transaction, Task task = null) {
//        SharedAnnotation.findAllByAnnotationClassNameAndAnnotationIdent(aa.class.name, aa.id).each {
//            sharedAnnotationService.delete(it,transaction,null,false)
//        }
//
//    }
//
//    def annotationTrackService
//    def deleteDependentAnnotationTrack(AlgoAnnotation ua, Transaction transaction, Task task = null) {
//        AnnotationTrack.findAllByAnnotationIdent(ua.id).each {
//            annotationTrackService.delete(it, transaction, task)
//        }
//    }

}