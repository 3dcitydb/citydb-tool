package org.citydb.web.controller;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.citydb.web.config.Constants;
import org.citydb.web.exception.ServiceException;
import org.citydb.web.schema.Exception;
import org.citydb.web.schema.LandingPage;
import org.citydb.web.service.CollectionService;
import org.citydb.web.service.FeatureService;
import org.citydb.web.service.PageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LandingPage.class)))
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
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LandingPage.class)))
    @ApiResponse(responseCode = "500", description = "Internal Server Error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Exception.class)))
    @GetMapping("/collections/{collectionId}")
    public ResponseEntity<Object> getCollection(@PathVariable("collectionId") String collectionId, HttpServletRequest request) {
        try {
            return new ResponseEntity<>(collectionService.getCollection(collectionId, request), HttpStatus.OK);
        } catch (ServiceException e) {
            return new ResponseEntity<>(Exception.of(e), e.getHttpStatus());
        }
    }

    @ApiResponse(responseCode = "200", description = "Fetch features of the feature collection with id collectionId.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LandingPage.class)))
    @ApiResponse(responseCode = "500", description = "Internal Server Error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Exception.class)))
    @GetMapping("/collections/{collectionId}/items")
    public ResponseEntity<Object> getCollectionFeatures(
            @PathVariable("collectionId") String collectionId, @RequestParam(value = "srid", required = false) Integer srid
    ) {
        try {
            return new ResponseEntity<>(featureService.getFeatureCollectionGeoJSON(collectionId, srid), HttpStatus.OK);
        } catch (ServiceException e) {
            return new ResponseEntity<>(Exception.of(e), e.getHttpStatus());
        }
    }
}