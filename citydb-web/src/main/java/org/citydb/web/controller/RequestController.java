package org.citydb.web.controller;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.citydb.web.config.Constants;
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
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.Objects;

@RestController
@RequestMapping(value = Constants.SERVICE_CONTEXT_PATH)
public class RequestController {
    private PageService pageService;
    private FeatureService featureService;
    private CollectionService collectionService;

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

    @GetMapping("")
    @ApiResponse(responseCode = "200", description = "Lading page",
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
                    @Content(mediaType = Constants.CITYGML_MEDIA_TYPE)
            }
    )
    @ApiResponse(responseCode = "500", description = "Internal Server Error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Exception.class)))
    @GetMapping("/collections/{collectionId}/items")
    public ResponseEntity<Object> getCollectionFeatures(
            HttpServletRequest request,
            @PathVariable("collectionId") String collectionId,
            @RequestParam(value = "srid", required = false) Integer srid
    ) {
        String contentType = request.getHeader("accept");
        if (Objects.equals(contentType, Constants.GEOJSON_MEDIA_TYPE)) {
            try {
                return new ResponseEntity<>(featureService.getFeatureCollectionGeoJSON(collectionId, srid), HttpStatus.OK);
            } catch (ServiceException e) {
                return new ResponseEntity<>(Exception.of(e), e.getHttpStatus());
            }
        } else {
            try {
                String filePath = "D:\\Daten\\Berlin\\all_wo_app\\data.gml";
                File file = new File(filePath);
                long fileSizeInBytes = file.length();
                FileInputStream fileInputStream = new FileInputStream(file);
                InputStreamResource inputStreamResource = new InputStreamResource(fileInputStream);
                return ResponseEntity.ok()
                        .contentLength(fileSizeInBytes)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header("Content-Disposition", "attachment; filename=\"" + filePath + "\"")
                        .body(inputStreamResource);
            } catch (java.lang.Exception e) {
                ServiceException serviceException = new ServiceException(e);
                return new ResponseEntity<>(Exception.of(serviceException), serviceException.getHttpStatus());
            }
        }
    }
}