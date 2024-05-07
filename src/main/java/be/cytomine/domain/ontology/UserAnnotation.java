package be.cytomine.domain.ontology;

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

import be.cytomine.domain.CytomineDomain;
import be.cytomine.domain.image.ImageInstance;
import be.cytomine.domain.image.SliceInstance;
import be.cytomine.domain.security.SecUser;
import be.cytomine.domain.security.User;
import be.cytomine.exceptions.WrongArgumentException;
import be.cytomine.service.UrlApi;
import be.cytomine.utils.JsonObject;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Getter
@Setter
public class UserAnnotation extends AnnotationDomain implements Serializable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    Integer countReviewedAnnotations = 0;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "annotation_term",
            joinColumns = { @JoinColumn(name = "user_annotation_id") },
            inverseJoinColumns = { @JoinColumn(name = "term_id") }
    )
    private List<Term> terms = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "annotation_track",
            joinColumns = { @JoinColumn(name = "annotation_ident") },
            inverseJoinColumns = { @JoinColumn(name = "track_id") }
    )
    private List<Track> tracks = new ArrayList<>();

    @PrePersist
    public void beforeCreate() {
        super.beforeCreate();
    }

    @PreUpdate
    public void beforeUpdate() {
        super.beforeUpdate();
    }

    @Override
    public List<Term> terms() {
        return terms;
    }

    /**
     * Check if annotation is reviewed
     * @return True if annotation is linked with at least one review annotation
     */
    boolean hasReviewedAnnotation() {
        return countReviewedAnnotations > 0;
    }

    public List<Track> tracks() {
        return tracks;
    }

    /**
     * Get all annotation terms id
     * @return Terms id list
     */
    public List<Long> termsId() {
        return terms().stream().map(CytomineDomain::getId).distinct().collect(Collectors.toList());

    }

    @Override
    public boolean isUserAnnotation() {
        return true;
    }

    public List<Long> tracksId() {
        return tracks().stream().map(CytomineDomain::getId).distinct().collect(Collectors.toList());

    }

    /**
     * Get all terms for automatic review
     * If review is done "for all" (without manual user control), we add these term to the new review annotation
     * @return
     */
    public List<Term> termsForReview() {
        return terms().stream().distinct().collect(Collectors.toList());
    }

    @Override
    Long getUserId() {
        return user.getId();
    }

    /**
     * Check if its a review annotation
     */
    public boolean isReviewedAnnotation() {
        return false;
    }

    @Override
    public boolean isRoiAnnotation() {
        return false;
    }


// TODO: seems to be not used
//    /**
//     * Get a list of each term link with annotation
//     * For each term, add all users that add this term
//     * [{id: x, term: y, user: [a,b,c]}, {...]
//     */
//    def usersIdByTerm() {
//        def results = []
//        if (this.version != null) {
//            AnnotationTerm.findAllByUserAnnotationAndDeletedIsNull(this).each { annotationTerm ->
//                    def map = [:]
//                map.id = annotationTerm.id
//                map.term = annotationTerm.term?.id
//                map.user = [annotationTerm.user?.id]
//                def item = results.find { it.term == annotationTerm.term?.id }
//                if (!item) {
//                    results << map
//                } else {
//                    item.user.add(annotationTerm.user.id)
//                }
//            }
//        }
//        results
//    }

    public CytomineDomain buildDomainFromJson(JsonObject json, EntityManager entityManager) {
        UserAnnotation annotation = this;
        annotation.id = json.getJSONAttrLong("id",null);

        if (json.containsKey("sliceObject")) {
            annotation.slice = (SliceInstance) json.get("sliceObject");
        } else {
            annotation.slice = (SliceInstance)json.getJSONAttrDomain(entityManager, "slice", new SliceInstance(), true);
        }

        if (json.containsKey("imageObject")) {
            annotation.image = (ImageInstance) json.get("imageObject");
        } else {
            annotation.image = (ImageInstance)json.getJSONAttrDomain(entityManager, "image", new ImageInstance(), true);
        }

        if (json.containsKey("userObject")) {
            annotation.user = (User) json.get("userObject");
        } else {
            annotation.user = (User)json.getJSONAttrDomain(entityManager, "user", new User(), true);
        }

        annotation.project = image.getProject();

        annotation.geometryCompression = json.getJSONAttrDouble("geometryCompression",0D);

        annotation.created = json.getJSONAttrDate("created");
        annotation.updated = json.getJSONAttrDate("updated");


        if (json.containsKey("location") && json.get("location") instanceof Geometry) {
            annotation.location = (Geometry) json.get("location");
        } else {
            try {
                annotation.location = new WKTReader().read(json.getJSONAttrStr("location"));
            }
            catch (ParseException ex) {
                throw new WrongArgumentException(ex.toString());
            }
        }

        if (annotation.location==null) {
            throw new WrongArgumentException("Geometry is null: 0 points");
        }

        if (annotation.location.getNumPoints() < 1) {
            throw new WrongArgumentException("Geometry is empty:" + annotation.location.getNumPoints() + " points");
        }

        return annotation;
    }

    @Override
    public JsonObject toJsonObject() {
        return getDataFromDomain(this);
    }

    public static JsonObject getDataFromDomain(CytomineDomain domain) {
        JsonObject returnArray = AnnotationDomain.getDataFromDomain(domain);
        UserAnnotation annotation = (UserAnnotation)domain;
        returnArray.put("cropURL", UrlApi.getUserAnnotationCropWithAnnotationId(annotation.getId(), "png"));
        returnArray.put("smallCropURL", UrlApi.getUserAnnotationCropWithAnnotationIdWithMaxSize(annotation.getId(), 256, "png"));
        returnArray.put("url", UrlApi.getUserAnnotationCropWithAnnotationId(annotation.getId(), "png"));
        returnArray.put("imageURL", UrlApi.getAnnotationURL(annotation.getImage().getProject().getId(), annotation.getImage().getId(), annotation.getId()));
        returnArray.put("reviewed", annotation.hasReviewedAnnotation());
        // TODO returnArray.put("track", domain?.tracksId());

        return returnArray;
    }

    @Override
    public SecUser user() {
        return user;
    }

    @Override
    public User userDomainCreator() {
        return user;
    }


}
