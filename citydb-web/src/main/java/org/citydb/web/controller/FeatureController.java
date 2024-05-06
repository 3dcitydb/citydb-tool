package org.citydb.web.controller;

import org.citydb.web.model.Collection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
public class FeatureController {

    @GetMapping("/collections/{collectionId}/items")
    public ResponseEntity<List<Collection>> getCollectionItmes(@RequestParam(required = false) String title) {
        try {
            List<Collection> collections = new ArrayList<>();

            return new ResponseEntity<>(collections, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}