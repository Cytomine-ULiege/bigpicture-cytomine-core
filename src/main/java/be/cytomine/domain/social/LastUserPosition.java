package be.cytomine.domain.social;

import be.cytomine.domain.CytomineSocialDomain;
import be.cytomine.domain.image.ImageInstance;
import be.cytomine.domain.image.SliceInstance;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.SecUser;
import be.cytomine.utils.DateUtils;
import be.cytomine.utils.JsonObject;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static be.cytomine.domain.social.PersistentUserPosition.getJtsPolygon;

@Getter
@Setter
@Document
public class LastUserPosition extends CytomineSocialDomain  {

    protected Long id;

    @CreatedDate
    protected Date created;

    @LastModifiedDate
    protected Date updated;

    protected Long user;

    private Long image;

    private Long slice;

    private Long project;

    private String imageName;

    /**
     * User screen area
     */
    @ElementCollection
    List<List<Double>> location;

    /**
     * User zoom on image
     */
    int zoom;

    Double  rotation;

    /**
     * Whether or not the user has decided to broadcast its position
     */
    boolean broadcast;


    public Long computeDateInMillis() {
        return created != null ? created.getTime() - new Date(0).getTime() : null;
    }

    public static JsonObject getDataFromDomain(CytomineSocialDomain domain) {
        JsonObject returnArray = new JsonObject();
        LastUserPosition position = (LastUserPosition)domain;
        returnArray.put("class", position.getClass());
        returnArray.put("id", position.getId());
        returnArray.put("created", DateUtils.getTimeToString(position.created));
        returnArray.put("updated", DateUtils.getTimeToString(position.updated));
        returnArray.put("user", position.getUser());
        returnArray.put("image", position.getImage());
        returnArray.put("slice", position.getSlice());
        returnArray.put("project", position.getProject());
        returnArray.put("zoom", position.getZoom());
        returnArray.put("rotation", position.getRotation());
        returnArray.put("broadcast", position.isBroadcast());
        Polygon polygon = getJtsPolygon(position.location);
        returnArray.put("location", polygon.toString());
        returnArray.put("x", polygon.getCentroid().getX());
        returnArray.put("y", polygon.getCentroid().getY());
        return returnArray;
    }

    @Override
    public JsonObject toJsonObject() {
        return getDataFromDomain(this);
    }
}