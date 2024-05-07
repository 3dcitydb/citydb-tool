package org.citydb.web.controller;

import org.citydb.web.schema.Collection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
public class FeatureController {

    @GetMapping("/collections")
    public ResponseEntity<List<Collection>> getCollections() {
        try {
            List<Collection> collections = new ArrayList<>(
                    List.of(Collection.of("1")
                            .setTitle("collection 1")
                            .setDescription("my first test collection")
                    )
            );
            return new ResponseEntity<>(collections, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}