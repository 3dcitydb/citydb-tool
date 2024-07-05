package org.citydb.web.controller;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.citydb.config.common.SrsReference;
import org.citydb.operation.exporter.ExportOptions;
import org.citydb.query.Query;
import org.citydb.query.filter.Filter;
import org.citydb.query.filter.encoding.FilterParseException;
import org.citydb.query.filter.operation.SqlExpression;
import org.citydb.query.limit.CountLimit;
import org.citydb.sqlbuilder.join.Join;
import org.citydb.sqlbuilder.join.Joins;
import org.citydb.sqlbuilder.literal.StringLiteral;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;
import org.citydb.web.config.Constants;
import org.citydb.web.config.WebOptions;
import org.citydb.web.config.feature.FeatureType;
import org.citydb.web.exception.ServiceException;
import org.citydb.web.schema.Collection;
import org.citydb.web.schema.Collections;
import org.citydb.web.schema.Exception;
import org.citydb.web.schema.LandingPage;
import org.citydb.web.schema.geojson.FeatureGeoJSON;
import org.citydb.web.service.CollectionService;
import org.citydb.web.service.FeatureService;
import org.citydb.web.service.PageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping(value = Constants.SERVICE_CONTEXT_PATH)
public class RequestController {
    private PageService pageService;
    private FeatureService featureService;
    private CollectionService collectionService;
    private WebOptions webOptions;

    @Autowired
    public void setPageService(PageService service) {
        this.pageService = service;
    }

    @Autowired
    public void setFeatureService(FeatureService service) {
        this.featureService = service;
    }

    @Autowired
    public void setCollectionService(CollectionService service) {
        this.collectionService = service;
    }

    @Autowired
    public void setWebOptions(WebOptions webOptions) {
        this.webOptions = webOptions;
    }


    @GetMapping("")
    @ApiResponse(responseCode = "200", description = "${api.response.landingPage.200.description}",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LandingPage.class)))
    @ApiResponse(responseCode = "500", description = "Internal Server Error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Exception.class)))
    public ResponseEntity<Object> getLandingPage(HttpServletRequest request) {
        try {
            return new ResponseEntity<>(pageService.getLandingPage(request), HttpStatus.OK);
        } catch (ServiceException e) {
            return new ResponseEntity<>(Exception.of(e), e.getHttpStatus());
        }
    }

    @ApiResponse(responseCode = "200", description = "Get the feature collections shared by this API.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Collections.class)))
    @ApiResponse(responseCode = "500", description = "Internal Server Error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Exception.class)))
    @GetMapping("/collections")
    public ResponseEntity<Object> getCollections(HttpServletRequest request) {
        try {
            return new ResponseEntity<>(collectionService.getCollections(request), HttpStatus.OK);
        } catch (ServiceException e) {
            return new ResponseEntity<>(Exception.of(e), e.getHttpStatus());
        }
    }

    @ApiResponse(responseCode = "200", description = "Get the information about the feature collection with id collectionId.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Collection.class)))
    @ApiResponse(responseCode = "500", description = "Internal Server Error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Exception.class)))
    @GetMapping("/collections/{collectionId}")
    public ResponseEntity<Object> getCollection(HttpServletRequest request, @PathVariable("collectionId") String collectionId) {
        try {
            return new ResponseEntity<>(collectionService.getCollection(collectionId, request), HttpStatus.OK);
        } catch (ServiceException e) {
            return new ResponseEntity<>(Exception.of(e), e.getHttpStatus());
        }
    }

    @ApiResponse(responseCode = "200", description = "Fetch features of the feature collection with id collectionId.",
            content = {
                    @Content(mediaType = Constants.GEOJSON_MEDIA_TYPE, schema = @Schema(implementation = FeatureGeoJSON.class)),
                    @Content(mediaType = Constants.CITYJSON_MEDIA_TYPE),
                    @Content(mediaType = Constants.CITYGML_MEDIA_TYPE)
            }
    )
    @ApiResponse(responseCode = "500", description = "Internal Server Error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Exception.class)))
    @GetMapping("/collections/{collectionId}/items")
    public ResponseEntity<Object> getCollectionFeatures(
            HttpServletRequest request,
            @PathVariable("collectionId") String collectionId,
            @RequestParam(value = "crs", required = false) String crs,
            @RequestParam(value = "filter", required = false) String filter,
            @RequestParam(value = "limit", required = false) Integer limit) {
        try {
            ExportOptions exportOptions = new ExportOptions();
            if (crs != null) {
                exportOptions.setTargetSrs(new SrsReference().setIdentifier(crs));
            }

            Query query = new Query();

            FeatureType featureType = webOptions.getFeatureTypes().get(collectionId);
            if (featureType != null) {
                query.addFeatureType(featureType.getName(), featureType.getNamespace());
                if (filter != null) {
                    query.setFilter(Filter.ofText(filter));
                }
            } else {
                Table featureTable = Table.of("feature");
                Table propertyTable = Table.of("property");
                Table parentFeatureTable = Table.of("feature");
                Select select = Select.newInstance()
                        .select(featureTable.columns("id"))
                        .from(featureTable)
                        .join(Join.of(Joins.INNER_JOIN,
                                propertyTable,
                                "val_feature_id",
                                Operators.EQUAL_TO,
                                featureTable.column("id")))
                        .join(Join.of(Joins.INNER_JOIN,
                                parentFeatureTable,
                                "id",
                                Operators.EQUAL_TO,
                                propertyTable.column("feature_id")))
                        .where(Operators.eq(
                                parentFeatureTable.column("objectid"),
                                StringLiteral.of(collectionId)));
                query.setFilter(Filter.of(SqlExpression.of(select.toSql())));
            }

            if (limit != null) {
                query.setCountLimit(new CountLimit().setLimit(limit));
            }

            String contentType = request.getHeader("accept");
            Object result = featureService.getFeatureCollection(query, exportOptions, contentType);
            if (result instanceof Path path) {
                result = Files.readString(path);
            }

            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (ServiceException e) {
            return new ResponseEntity<>(Exception.of(e), e.getHttpStatus());
        } catch (FilterParseException | IOException e) {
            return new ResponseEntity<>(Exception.of(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}