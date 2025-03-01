DELETE FROM datatype;

-- Core Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1, null, 'Undefined', 1, 1, '{"identifier":"core:Undefined","description":"Undefined data type.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (2, null, 'Boolean', 0, 1, '{"identifier":"core:Boolean","description":"Boolean is a basic type and can have one of two values: true (represented as 1) or false (represented as 0).","table":"property","value":{"column":"val_int","type":"boolean"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (3, null, 'Integer', 0, 1, '{"identifier":"core:Integer","description":"Integer is a basic type that represents a whole number without fractional or decimal components.","table":"property","value":{"column":"val_int","type":"integer"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (4, null, 'Double', 0, 1, '{"identifier":"core:Double","description":"Double is a basic type that represents a double-precision floating-point number.","table":"property","value":{"column":"val_double","type":"double"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (5, null, 'String', 0, 1, '{"identifier":"core:String","description":"String is a basic type that represents a sequence of characters.","table":"property","value":{"column":"val_string","type":"string"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (6, null, 'URI', 0, 1, '{"identifier":"core:URI","description":"A URI (Uniform Resource Identifier) is a string to uniquely identify a resource, either by its location, name, or both, in a standardized format.","table":"property","value":{"column":"val_uri","type":"string"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (7, null, 'Timestamp', 0, 1, '{"identifier":"core:Timestamp","description":"Timestamp is a basic type that represents a specific point in time.","table":"property","value":{"column":"val_timestamp","type":"timestamp"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (8, null, 'AddressProperty', 0, 1, '{"identifier":"core:AddressProperty","description":"AddressProperty links a feature or property to an address.","table":"property","join":{"table":"address","fromColumn":"val_address_id","toColumn":"id"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (9, null, 'AppearanceProperty', 0, 1, '{"identifier":"core:AppearanceProperty","description":"AppearanceProperty links a feature or property to an appearance.","table":"property","join":{"table":"appearance","fromColumn":"val_appearance_id","toColumn":"id"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (10, null, 'FeatureProperty', 0, 1, '{"identifier":"core:FeatureProperty","description":"FeatureProperty links a feature or property to a feature.","table":"property","join":{"table":"feature","fromColumn":"val_feature_id","toColumn":"id","conditions":[{"column":"objectclass_id","value":"@target.objectclass_id@","type":"integer"}]}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (11, null, 'GeometryProperty', 0, 1, '{"identifier":"core:GeometryProperty","description":"GeometryProperty links a feature or property to a geometry.","table":"property","properties":[{"name":"lod","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Specifies the Level of Detail of the geometry.","value":{"column":"val_lod","type":"string"}}],"join":{"table":"geometry_data","fromColumn":"val_geometry_id","toColumn":"id"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (12, null, 'Reference', 0, 1, '{"identifier":"core:Reference","description":"Reference links a feature or property to a remote resource identified by a URI.","table":"property","value":{"column":"val_uri","type":"string"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (13, null, 'CityObjectRelation', 0, 1, '{"identifier":"core:CityObjectRelation","description":"CityObjectRelation represents a specific relation from the city object in which the relation is included to another city object.","table":"property","properties":[{"name":"relatedTo","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Relates other city objects to the CityObjectRelation.","type":"core:FeatureProperty","target":"core:AbstractCityObject","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"relationType","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Indicates the specific type of the CityObjectRelation.","type":"core:Code","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (14, null, 'Code', 0, 1, '{"identifier":"core:Code","description":"Code is a basic type for a string-based term, keyword, or name that can additionally have a code space.","table":"property","value":{"column":"val_string","type":"string"},"properties":[{"name":"codeSpace","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Specifies the code space of the term, typically a dictionary, thesaurus, classification scheme, authority, or pattern for the term.","value":{"column":"val_codespace","type":"string"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (15, null, 'ExternalReference', 0, 1, '{"identifier":"core:ExternalReference","description":"ExternalReference is a reference to a corresponding object in another information system, for example in the German cadastre (ALKIS), the German topographic information system (ATKIS), or the OS UK MasterMap.","table":"property","properties":[{"name":"targetResource","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Specifies the URI that points to the object in the external information system.","type":"core:URI"},{"name":"informationSystem","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Specifies the URI that points to the external information system.","value":{"column":"val_codespace","type":"string"}},{"name":"relationType","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Specifies a URI that additionally qualifies the ExternalReference. The URI can point to a definition from an external ontology (e.g. the sameAs relation from OWL) and allows for mapping the ExternalReference to RDF triples.","value":{"column":"val_string","type":"string"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (16, null, 'ImplicitGeometryProperty', 0, 1, '{"identifier":"core:ImplicitGeometryProperty","description":"ImplicitGeometryProperty links a feature or property to an implicit geometry.","table":"property","properties":[{"name":"transformationMatrix","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Specifies the mathematical transformation (translation, rotation, and scaling) between the prototypical geometry and the actual spatial position of the object.","value":{"column":"val_array","type":"doubleArray"}},{"name":"referencePoint","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Relates to a 3D Point geometry that represents the base point of the object in the coordinate system of the database.","value":{"column":"val_implicitgeom_refpoint","type":"core:Point"}}],"join":{"table":"implicit_geometry","fromColumn":"val_implicitgeom_id","toColumn":"id"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (17, null, 'Measure', 0, 1, '{"identifier":"core:Measure","description":"Measure is a basic type that represents an amount encoded as double value with a unit of measurement.","table":"property","value":{"column":"val_double","type":"double"},"properties":[{"name":"uom","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Specifies the unit of measurement of the amount.","value":{"column":"val_uom","type":"string"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (18, null, 'MeasureOrNilReasonList', 0, 1, '{"identifier":"core:MeasureOrNilReasonList","description":"MeasureOrNilReasonList is a basic type that represents a list of double values and/or nil reasons together with a unit of measurement.","table":"property","value":{"column":"val_array","type":"array","schema":{"items":{"type":["number","string"]}}},"properties":[{"name":"uom","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Specifies the unit of measurement of the values.","value":{"column":"val_uom","type":"string"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (19, null, 'Occupancy', 0, 1, '{"identifier":"core:Occupancy","description":"Occupancy is an application-dependent indication of what is contained by a feature.","table":"property","value":{"property":0},"properties":[{"name":"numberOfOccupants","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Indicates the number of occupants contained by a feature.","type":"core:Integer","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"interval","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Indicates the time period the occupants are contained by a feature.","type":"core:Code","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"occupantType","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Indicates the specific type of the occupants that are contained by a feature.","type":"core:Code","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (20, null, 'QualifiedArea', 0, 1, '{"identifier":"core:QualifiedArea","description":"QualifiedArea is an application-dependent measure of the area of a space or of a thematic surface.","table":"property","value":{"property":0},"properties":[{"name":"area","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Specifies the value of the QualifiedArea.","type":"core:Measure"},{"name":"typeOfArea","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Indicates the specific type of the QualifiedArea.","type":"core:Code"}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (21, null, 'QualifiedVolume', 0, 1, '{"identifier":"core:QualifiedVolume","description":"QualifiedVolume is an application-dependent measure of the volume of a space.","table":"property","value":{"property":0},"properties":[{"name":"volume","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Specifies the value of the QualifiedVolume.","type":"core:Measure"},{"name":"typeOfVolume","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Indicates the specific type of the QualifiedVolume.","type":"core:Code"}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (22, null, 'StringOrRef', 0, 1, '{"identifier":"core:StringOrRef","description":"StringOrRef is a basic type that represents either a simple string or a reference to a remote string value.","table":"property","value":{"column":"val_string","type":"string"},"properties":[{"name":"href","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","description":"Specifies the reference to a remote string value.","value":{"column":"val_uri","type":"string"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (23, null, 'TimePosition', 0, 1, '{"identifier":"core:TimePosition","description":"TimePosition is a basic type that represents a specific temporal position, stored as a timestamp. Local times are converted to timestamps by adding ''0001-01-01T00:00:00Z'' as the base date.","table":"property","value":{"column":"val_timestamp","type":"timestamp"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (24, null, 'Duration', 0, 1, '{"identifier":"core:Duration","description":"Duration is a basic type that represents a duration of time encoded as ISO 8601 compliant string.","table":"property","value":{"column":"val_string","type":"string"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (50, null, 'ADEOfAbstractCityObject', 1, 1, '{"identifier":"core:ADEOfAbstractCityObject","description":"ADEOfAbstractCityObject acts as a hook to define properties within an ADE that are to be added to AbstractCityObject.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (51, null, 'ADEOfAbstractDynamizer', 1, 1, '{"identifier":"core:ADEOfAbstractDynamizer","description":"ADEOfAbstractDynamizer acts as a hook to define properties within an ADE that are to be added to AbstractDynamizer.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (52, null, 'ADEOfAbstractFeature', 1, 1, '{"identifier":"core:ADEOfAbstractFeature","description":"ADEOfAbstractFeature acts as a hook to define properties within an ADE that are to be added to AbstractFeature.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (53, null, 'ADEOfAbstractFeatureWithLifespan', 1, 1, '{"identifier":"core:ADEOfAbstractFeatureWithLifespan","description":"ADEOfAbstractFeatureWithLifespan acts as a hook to define properties within an ADE that are to be added to AbstractFeatureWithLifespan.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (54, null, 'ADEOfAbstractLogicalSpace', 1, 1, '{"identifier":"core:ADEOfAbstractLogicalSpace","description":"ADEOfAbstractLogicalSpace acts as a hook to define properties within an ADE that are to be added to AbstractLogicalSpace.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (55, null, 'ADEOfAbstractOccupiedSpace', 1, 1, '{"identifier":"core:ADEOfAbstractOccupiedSpace","description":"ADEOfAbstractOccupiedSpace acts as a hook to define properties within an ADE that are to be added to AbstractOccupiedSpace.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (56, null, 'ADEOfAbstractPhysicalSpace', 1, 1, '{"identifier":"core:ADEOfAbstractPhysicalSpace","description":"ADEOfAbstractPhysicalSpace acts as a hook to define properties within an ADE that are to be added to AbstractPhysicalSpace.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (57, null, 'ADEOfAbstractPointCloud', 1, 1, '{"identifier":"core:ADEOfAbstractPointCloud","description":"ADEOfAbstractPointCloud acts as a hook to define properties within an ADE that are to be added to AbstractPointCloud.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (58, null, 'ADEOfAbstractSpace', 1, 1, '{"identifier":"core:ADEOfAbstractSpace","description":"ADEOfAbstractSpace acts as a hook to define properties within an ADE that are to be added to AbstractSpace.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (59, null, 'ADEOfAbstractSpaceBoundary', 1, 1, '{"identifier":"core:ADEOfAbstractSpaceBoundary","description":"ADEOfAbstractSpaceBoundary acts as a hook to define properties within an ADE that are to be added to AbstractSpaceBoundary.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (60, null, 'ADEOfAbstractThematicSurface', 1, 1, '{"identifier":"core:ADEOfAbstractThematicSurface","description":"ADEOfAbstractThematicSurface acts as a hook to define properties within an ADE that are to be added to AbstractThematicSurface.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (61, null, 'ADEOfAbstractUnoccupiedSpace', 1, 1, '{"identifier":"core:ADEOfAbstractUnoccupiedSpace","description":"ADEOfAbstractUnoccupiedSpace acts as a hook to define properties within an ADE that are to be added to AbstractUnoccupiedSpace.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (62, null, 'ADEOfAbstractVersion', 1, 1, '{"identifier":"core:ADEOfAbstractVersion","description":"ADEOfAbstractVersion acts as a hook to define properties within an ADE that are to be added to AbstractVersion.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (63, null, 'ADEOfAbstractVersionTransition', 1, 1, '{"identifier":"core:ADEOfAbstractVersionTransition","description":"ADEOfAbstractVersionTransition acts as a hook to define properties within an ADE that are to be added to AbstractVersionTransition.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (64, null, 'ADEOfCityModel', 1, 1, '{"identifier":"core:ADEOfCityModel","description":"ADEOfCityModel acts as a hook to define properties within an ADE that are to be added to a CityModel.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (65, null, 'ADEOfClosureSurface', 1, 1, '{"identifier":"core:ADEOfClosureSurface","description":"ADEOfClosureSurface acts as a hook to define properties within an ADE that are to be added to a ClosureSurface.","table":"property"}');

-- Dynamizer Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (100, null, 'AbstractTimeValuePair', 1, 2, '{"identifier":"dyn:AbstractTimeValuePair","description":"AbstractTimeValuePair is the abstract superclass for value types that are valid for a given timepoint.","table":"property","properties":[{"name":"timestamp","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the timepoint at which the value of the AbstractTimeValuePair is valid.","type":"core:TimePosition"}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (101, null, 'AttributeReference', 0, 2, '{"identifier":"dyn:AttributeReference","description":"AttributeReference specifies a reference to a specific attribute of a feature.","table":"property","value":{"column":"val_string","type":"string"},"properties":[{"name":"id","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the internal database ID of the target attribute.","value":{"column":"val_int","type":"integer"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (102, null, 'SensorConnection', 0, 2, '{"identifier":"dyn:SensorConnection","description":"A SensorConnection provides all details that are required to retrieve a specific data stream from an external sensor web service. This data type comprises the service type (e.g. OGC SensorThings API, OGC Sensor Observation Services, MQTT, proprietary platforms), the URL of the sensor service, the identifier for the sensor or thing, and its observed property as well as information about the required authentication method.","table":"property","properties":[{"name":"connectionType","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Indicates the type of Sensor API to which the SensorConnection refers.","type":"core:Code","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"observationProperty","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the phenomenon for which the SensorConnection provides observations.","type":"core:String","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"uom","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the unit of measurement of the observations.","type":"core:String","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"sensorID","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the unique identifier of the sensor from which the SensorConnection retrieves observations.","type":"core:String","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"sensorName","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the name of the sensor from which the SensorConnection retrieves observations.","type":"core:String","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"observationID","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the unique identifier of the observation that is retrieved by the SensorConnection.","type":"core:String","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"datastreamID","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the datastream that is retrieved by the SensorConnection.","type":"core:String","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"baseURL","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the base URL of the Sensor API request.","type":"core:URI","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"authType","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the type of authentication required to be able to access the Sensor API.","type":"core:Code","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"mqttServer","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the name of the MQTT Server. This attribute is relevant when the MQTT Protocol is used to connect to a Sensor API.","type":"core:String","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"mqttTopic","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Names the specific datastream that is retrieved by the SensorConnection. This attribute is relevant when the MQTT Protocol is used to connect to a Sensor API.","type":"core:String","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"linkToObservation","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the complete URL to the observation request.","type":"core:String","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"linkToSensorDescription","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the complete URL to the sensor description request.","type":"core:String","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"sensorLocation","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Relates the sensor to the city object where it is located.","type":"core:FeatureProperty","target":"core:AbstractCityObject","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (103, 100, 'TimeAppearance', 0, 2, '{"identifier":"dyn:TimeAppearance","description":"TimeAppearance represents an appearance value that is valid for a given timepoint.","table":"property","properties":[{"name":"appearanceValue","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the appearance value at the given timepoint.","type":"core:AppearanceProperty","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (104, 100, 'TimeBoolean', 0, 2, '{"identifier":"dyn:TimeBoolean","description":"TimeBoolean represents a boolean value that is valid for a given timepoint.","table":"property","properties":[{"name":"boolValue","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the boolean value at the given timepoint.","type":"core:Boolean"}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (105, 100, 'TimeDouble', 0, 2, '{"identifier":"dyn:TimeDouble","description":"TimeDouble represents a double value that is valid for a given timepoint.","table":"property","properties":[{"name":"doubleValue","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the double value at the given timepoint.","type":"core:Double"}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (106, 100, 'TimeGeometry', 0, 2, '{"identifier":"dyn:TimeGeometry","description":"TimeGeometry represents a geometry value that is valid for a given timepoint.","table":"property","properties":[{"name":"geometryValue","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the geometry value at the given timepoint.","type":"core:GeometryProperty","target":"core:AbstractGeometry","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (107, 100, 'TimeImplicitGeometry', 0, 2, '{"identifier":"dyn:TimeImplicitGeometry","description":"TimeImplicitGeometry represents an implicit geometry value that is valid for a given timepoint.","table":"property","properties":[{"name":"implicitGeometryValue","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the implicit geometry value at the given timepoint.","type":"core:ImplicitGeometryProperty","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (108, 100, 'TimeInteger', 0, 2, '{"identifier":"dyn:TimeInteger","description":"TimeInteger represents an integer value that is valid for a given timepoint.","table":"property","properties":[{"name":"intValue","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the integer value at the given timepoint.","type":"core:Integer"}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (109, null, 'TimeseriesComponent', 0, 2, '{"identifier":"dyn:TimeseriesComponent","description":"TimeseriesComponent represents an element of a CompositeTimeseries.","table":"property","properties":[{"name":"repetitions","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies how often the timeseries that is referenced by the TimeseriesComponent should be iterated.","type":"core:Integer","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"additionalGap","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies how much extra time is added after all repetitions as an additional gap.","type":"core:Duration","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"timeseries","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Relates a timeseries to the TimeseriesComponent.","type":"core:FeatureProperty","target":"dyn:AbstractTimeseries","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (110, 100, 'TimeString', 0, 2, '{"identifier":"dyn:TimeString","description":"TimeString represents a string value that is valid for a given timepoint.","table":"property","properties":[{"name":"stringValue","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the string value at the given timepoint.","type":"core:String"}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (111, 100, 'TimeURI', 0, 2, '{"identifier":"dyn:TimeURI","description":"TimeURI represents a URI value that is valid for a given timepoint.","table":"property","properties":[{"name":"uriValue","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","description":"Specifies the URI value at the given timepoint.","type":"core:URI"}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (112, null, 'ADEOfAbstractAtomicTimeseries', 1, 2, '{"identifier":"dyn:ADEOfAbstractAtomicTimeseries","description":"ADEOfAbstractAtomicTimeseries acts as a hook to define properties within an ADE that are to be added to AbstractAtomicTimeseries.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (113, null, 'ADEOfAbstractTimeseries', 1, 2, '{"identifier":"dyn:ADEOfAbstractTimeseries","description":"ADEOfAbstractTimeseries acts as a hook to define properties within an ADE that are to be added to AbstractTimeseries.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (114, null, 'ADEOfCompositeTimeseries', 1, 2, '{"identifier":"dyn:ADEOfCompositeTimeseries","description":"ADEOfCompositeTimeseries acts as a hook to define properties within an ADE that are to be added to a CompositeTimeseries.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (115, null, 'ADEOfDynamizer', 1, 2, '{"identifier":"dyn:ADEOfDynamizer","description":"ADEOfDynamizer acts as a hook to define properties within an ADE that are to be added to a Dynamizer.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (116, null, 'ADEOfGenericTimeseries', 1, 2, '{"identifier":"dyn:ADEOfGenericTimeseries","description":"ADEOfGenericTimeseries acts as a hook to define properties within an ADE that are to be added to a GenericTimeseries.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (117, null, 'ADEOfStandardFileTimeseries', 1, 2, '{"identifier":"dyn:ADEOfStandardFileTimeseries","description":"ADEOfStandardFileTimeseries acts as a hook to define properties within an ADE that are to be added to a StandardFileTimeseries.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (118, null, 'ADEOfTabulatedFileTimeseries', 1, 2, '{"identifier":"dyn:ADEOfTabulatedFileTimeseries","description":"ADEOfTabulatedFileTimeseries acts as a hook to define properties within an ADE that are to be added to a TabulatedFileTimeseries.","table":"property"}');

-- Generics Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (200, null, 'GenericAttributeSet', 0, 3, '{"identifier":"gen:GenericAttributeSet","description":"A GenericAttributeSet is a named collection of generic attributes.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (201, null, 'ADEOfGenericLogicalSpace', 1, 3, '{"identifier":"gen:ADEOfGenericLogicalSpace","description":"ADEOfGenericLogicalSpace acts as a hook to define properties within an ADE that are to be added to a GenericLogicalSpace.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (202, null, 'ADEOfGenericOccupiedSpace', 1, 3, '{"identifier":"gen:ADEOfGenericOccupiedSpace","description":"ADEOfGenericOccupiedSpace acts as a hook to define properties within an ADE that are to be added to a GenericOccupiedSpace.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (203, null, 'ADEOfGenericThematicSurface', 1, 3, '{"identifier":"gen:ADEOfGenericThematicSurface","description":"ADEOfGenericThematicSurface acts as a hook to define properties within an ADE that are to be added to a GenericThematicSurface.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (204, null, 'ADEOfGenericUnoccupiedSpace', 1, 3, '{"identifier":"gen:ADEOfGenericUnoccupiedSpace","description":"ADEOfGenericUnoccupiedSpace acts as a hook to define properties within an ADE that are to be added to a GenericUnoccupiedSpace.","table":"property"}');

-- LandUse Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (300, null, 'ADEOfLandUse', 1, 4, '{"identifier":"luse:ADEOfLandUse","description":"ADEOfLandUse acts as a hook to define properties within an ADE that are to be added to a LandUse.","table":"property"}');

-- PointCloud Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (400, null, 'ADEOfPointCloud', 1, 5, '{"identifier":"pcl:ADEOfPointCloud","description":"ADEOfPointCloud acts as a hook to define properties within an ADE that are to be added to a PointCloud.","table":"property"}');

-- Relief Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (500, null, 'ADEOfAbstractReliefComponent', 1, 6, '{"identifier":"dem:ADEOfAbstractReliefComponent","description":"ADEOfAbstractReliefComponent acts as a hook to define properties within an ADE that are to be added to AbstractReliefComponent.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (501, null, 'ADEOfBreaklineRelief', 1, 6, '{"identifier":"dem:ADEOfBreaklineRelief","description":"ADEOfBreaklineRelief acts as a hook to define properties within an ADE that are to be added to a BreaklineRelief.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (502, null, 'ADEOfMassPointRelief', 1, 6, '{"identifier":"dem:ADEOfMassPointRelief","description":"ADEOfMassPointRelief acts as a hook to define properties within an ADE that are to be added to a MassPointRelief.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (503, null, 'ADEOfRasterRelief', 1, 6, '{"identifier":"dem:ADEOfRasterRelief","description":"ADEOfRasterRelief acts as a hook to define properties within an ADE that are to be added to a RasterRelief.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (504, null, 'ADEOfReliefFeature', 1, 6, '{"identifier":"dem:ADEOfReliefFeature","description":"ADEOfReliefFeature acts as a hook to define properties within an ADE that are to be added to a ReliefFeature.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (505, null, 'ADEOfTINRelief', 1, 6, '{"identifier":"dem:ADEOfTINRelief","description":"ADEOfTINRelief acts as a hook to define properties within an ADE that are to be added to a TINRelief.","table":"property"}');

-- Transportation Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (600, null, 'ADEOfAbstractTransportationSpace', 1, 7, '{"identifier":"tran:ADEOfAbstractTransportationSpace","description":"ADEOfAbstractTransportationSpace acts as a hook to define properties within an ADE that are to be added to AbstractTransportationSpace.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (601, null, 'ADEOfAuxiliaryTrafficArea', 1, 7, '{"identifier":"tran:ADEOfAuxiliaryTrafficArea","description":"ADEOfAuxiliaryTrafficArea acts as a hook to define properties within an ADE that are to be added to an AuxiliaryTrafficArea.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (602, null, 'ADEOfAuxiliaryTrafficSpace', 1, 7, '{"identifier":"tran:ADEOfAuxiliaryTrafficSpace","description":"ADEOfAuxiliaryTrafficSpace acts as a hook to define properties within an ADE that are to be added to an AuxiliaryTrafficSpace.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (603, null, 'ADEOfClearanceSpace', 1, 7, '{"identifier":"tran:ADEOfClearanceSpace","description":"ADEOfClearanceSpace acts as a hook to define properties within an ADE that are to be added to a ClearanceSpace.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (604, null, 'ADEOfHole', 1, 7, '{"identifier":"tran:ADEOfHole","description":"ADEOfHole acts as a hook to define properties within an ADE that are to be added to a Hole.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (605, null, 'ADEOfHoleSurface', 1, 7, '{"identifier":"tran:ADEOfHoleSurface","description":"ADEOfHoleSurface acts as a hook to define properties within an ADE that are to be added to a HoleSurface.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (606, null, 'ADEOfIntersection', 1, 7, '{"identifier":"tran:ADEOfIntersection","description":"ADEOfIntersection acts as a hook to define properties within an ADE that are to be added to an Intersection.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (607, null, 'ADEOfMarking', 1, 7, '{"identifier":"tran:ADEOfMarking","description":"ADEOfMarking acts as a hook to define properties within an ADE that are to be added to a Marking.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (608, null, 'ADEOfRailway', 1, 7, '{"identifier":"tran:ADEOfRailway","description":"ADEOfRailway acts as a hook to define properties within an ADE that are to be added to a Railway.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (609, null, 'ADEOfRoad', 1, 7, '{"identifier":"tran:ADEOfRoad","description":"ADEOfRoad acts as a hook to define properties within an ADE that are to be added to a Road.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (610, null, 'ADEOfSection', 1, 7, '{"identifier":"tran:ADEOfSection","description":"ADEOfSection acts as a hook to define properties within an ADE that are to be added to a Section.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (611, null, 'ADEOfSquare', 1, 7, '{"identifier":"tran:ADEOfSquare","description":"ADEOfSquare acts as a hook to define properties within an ADE that are to be added to a Square.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (612, null, 'ADEOfTrack', 1, 7, '{"identifier":"tran:ADEOfTrack","description":"ADEOfTrack acts as a hook to define properties within an ADE that are to be added to a Track.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (613, null, 'ADEOfTrafficArea', 1, 7, '{"identifier":"tran:ADEOfTrafficArea","description":"ADEOfTrafficArea acts as a hook to define properties within an ADE that are to be added to a TrafficArea.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (614, null, 'ADEOfTrafficSpace', 1, 7, '{"identifier":"tran:ADEOfTrafficSpace","description":"ADEOfTrafficSpace acts as a hook to define properties within an ADE that are to be added to a TrafficSpace.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (615, null, 'ADEOfWaterway', 1, 7, '{"identifier":"tran:ADEOfWaterway","description":"ADEOfWaterway acts as a hook to define properties within an ADE that are to be added to a Waterway.","table":"property"}');

-- Construction Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (700, null, 'ConstructionEvent', 0, 8, '{"identifier":"con:ConstructionEvent","description":"A ConstructionEvent is a data type used to describe a specific event that is associated with a construction. Examples are the issuing of a building permit or the renovation of a building.","table":"property","value":{"property":0},"properties":[{"name":"event","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","description":"Indicates the specific event type.","type":"core:Code","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"dateOfEvent","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","description":"Specifies the date and time at which the event took or will take place.","type":"core:Timestamp","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"description","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","description":"Provides additional information on the event.","type":"core:String","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (701, null, 'Elevation', 0, 8, '{"identifier":"con:Elevation","description":"Elevation is a data type that includes the elevation value itself and information on how this elevation was measured.","table":"property","properties":[{"name":"elevationValue","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","description":"Specifies the value of the elevation.","value":{"column":"val_array","type":"doubleArray"}},{"name":"elevationReference","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","description":"Specifies the level from which the elevation was measured.","type":"core:Code"}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (702, null, 'Height', 0, 8, '{"identifier":"con:Height","description":"Height represents a vertical distance (measured or estimated) between a low reference and a high reference.","table":"property","value":{"property":0},"properties":[{"name":"value","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","description":"Specifies the value of the height above or below ground.","type":"core:Measure","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"status","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","description":"Indicates the way the height has been captured.","type":"core:String","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"lowReference","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","description":"Indicates the low point used to calculate the value of the height.","type":"core:Code","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"highReference","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","description":"Indicates the high point used to calculate the value of the height.","type":"core:Code","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (703, null, 'ADEOfAbstractConstruction', 1, 8, '{"identifier":"con:ADEOfAbstractConstruction","description":"ADEOfAbstractConstruction acts as a hook to define properties within an ADE that are to be added to AbstractConstruction.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (704, null, 'ADEOfAbstractConstructionSurface', 1, 8, '{"identifier":"con:ADEOfAbstractConstructionSurface","description":"ADEOfAbstractConstructionSurface acts as a hook to define properties within an ADE that are to be added to AbstractConstructionSurface.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (705, null, 'ADEOfAbstractConstructiveElement', 1, 8, '{"identifier":"con:ADEOfAbstractConstructiveElement","description":"ADEOfAbstractConstructiveElement acts as a hook to define properties within an ADE that are to be added to AbstractConstructiveElement.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (706, null, 'ADEOfAbstractFillingElement', 1, 8, '{"identifier":"con:ADEOfAbstractFillingElement","description":"ADEOfAbstractFillingElement acts as a hook to define properties within an ADE that are to be added to AbstractFillingElement.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (707, null, 'ADEOfAbstractFillingSurface', 1, 8, '{"identifier":"con:ADEOfAbstractFillingSurface","description":"ADEOfAbstractFillingSurface acts as a hook to define properties within an ADE that are to be added to AbstractFillingSurface.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (708, null, 'ADEOfAbstractFurniture', 1, 8, '{"identifier":"con:ADEOfAbstractFurniture","description":"ADEOfAbstractFurniture acts as a hook to define properties within an ADE that are to be added to AbstractFurniture.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (709, null, 'ADEOfAbstractInstallation', 1, 8, '{"identifier":"con:ADEOfAbstractInstallation","description":"ADEOfAbstractInstallation acts as a hook to define properties within an ADE that are to be added to AbstractInstallation.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (710, null, 'ADEOfCeilingSurface', 1, 8, '{"identifier":"con:ADEOfCeilingSurface","description":"ADEOfCeilingSurface acts as a hook to define properties within an ADE that are to be added to a CeilingSurface.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (711, null, 'ADEOfDoor', 1, 8, '{"identifier":"con:ADEOfDoor","description":"ADEOfDoor acts as a hook to define properties within an ADE that are to be added to a Door.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (712, null, 'ADEOfDoorSurface', 1, 8, '{"identifier":"con:ADEOfDoorSurface","description":"ADEOfDoorSurface acts as a hook to define properties within an ADE that are to be added to a DoorSurface.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (713, null, 'ADEOfFloorSurface', 1, 8, '{"identifier":"con:ADEOfFloorSurface","description":"ADEOfFloorSurface acts as a hook to define properties within an ADE that are to be added to a FloorSurface.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (714, null, 'ADEOfGroundSurface', 1, 8, '{"identifier":"con:ADEOfGroundSurface","description":"ADEOfGroundSurface acts as a hook to define properties within an ADE that are to be added to a GroundSurface.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (715, null, 'ADEOfInteriorWallSurface', 1, 8, '{"identifier":"con:ADEOfInteriorWallSurface","description":"ADEOfInteriorWallSurface acts as a hook to define properties within an ADE that are to be added to an InteriorWallSurface.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (716, null, 'ADEOfOtherConstruction', 1, 8, '{"identifier":"con:ADEOfOtherConstruction","description":"ADEOfOtherConstruction acts as a hook to define properties within an ADE that are to be added to an OtherConstruction.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (717, null, 'ADEOfOuterCeilingSurface', 1, 8, '{"identifier":"con:ADEOfOuterCeilingSurface","description":"ADEOfOuterCeilingSurface acts as a hook to define properties within an ADE that are to be added to an OuterCeilingSurface.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (718, null, 'ADEOfOuterFloorSurface', 1, 8, '{"identifier":"con:ADEOfOuterFloorSurface","description":"ADEOfOuterFloorSurface acts as a hook to define properties within an ADE that are to be added to an OuterFloorSurface.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (719, null, 'ADEOfRoofSurface', 1, 8, '{"identifier":"con:ADEOfRoofSurface","description":"ADEOfRoofSurface acts as a hook to define properties within an ADE that are to be added to a RoofSurface.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (720, null, 'ADEOfWallSurface', 1, 8, '{"identifier":"con:ADEOfWallSurface","description":"ADEOfWallSurface acts as a hook to define properties within an ADE that are to be added to a WallSurface.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (721, null, 'ADEOfWindow', 1, 8, '{"identifier":"con:ADEOfWindow","description":"ADEOfWindow acts as a hook to define properties within an ADE that are to be added to a Window.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (722, null, 'ADEOfWindowSurface', 1, 8, '{"identifier":"con:ADEOfWindowSurface","description":"ADEOfWindowSurface acts as a hook to define properties within an ADE that are to be added to a WindowSurface.","table":"property"}');

-- Tunnel Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (800, null, 'ADEOfAbstractTunnel', 1, 9, '{"identifier":"tun:ADEOfAbstractTunnel","description":"ADEOfAbstractTunnel acts as a hook to define properties within an ADE that are to be added to AbstractTunnel.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (801, null, 'ADEOfHollowSpace', 1, 9, '{"identifier":"tun:ADEOfHollowSpace","description":"ADEOfHollowSpace acts as a hook to define properties within an ADE that are to be added to a HollowSpace.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (802, null, 'ADEOfTunnel', 1, 9, '{"identifier":"tun:ADEOfTunnel","description":"ADEOfTunnel acts as a hook to define properties within an ADE that are to be added to a Tunnel.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (803, null, 'ADEOfTunnelConstructiveElement', 1, 9, '{"identifier":"tun:ADEOfTunnelConstructiveElement","description":"ADEOfTunnelConstructiveElement acts as a hook to define properties within an ADE that are to be added to a TunnelConstructiveElement.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (804, null, 'ADEOfTunnelFurniture', 1, 9, '{"identifier":"tun:ADEOfTunnelFurniture","description":"ADEOfTunnelFurniture acts as a hook to define properties within an ADE that are to be added to a TunnelFurniture.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (805, null, 'ADEOfTunnelInstallation', 1, 9, '{"identifier":"tun:ADEOfTunnelInstallation","description":"ADEOfTunnelInstallation acts as a hook to define properties within an ADE that are to be added to a TunnelInstallation.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (806, null, 'ADEOfTunnelPart', 1, 9, '{"identifier":"tun:ADEOfTunnelPart","description":"ADEOfTunnelPart acts as a hook to define properties within an ADE that are to be added to a TunnelPart.","table":"property"}');

-- Building Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (900, null, 'RoomHeight', 0, 10, '{"identifier":"bldg:RoomHeight","description":"The RoomHeight represents a vertical distance (measured or estimated) between a low reference and a high reference.","table":"property","value":{"property":0},"properties":[{"name":"value","namespace":"http://3dcitydb.org/3dcitydb/building/5.0","description":"Specifies the value of the room height.","type":"core:Measure","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"status","namespace":"http://3dcitydb.org/3dcitydb/building/5.0","description":"Indicates the way the room height has been captured.","type":"core:String","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"lowReference","namespace":"http://3dcitydb.org/3dcitydb/building/5.0","description":"Indicates the low point used to calculate the value of the room height.","type":"core:Code","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"highReference","namespace":"http://3dcitydb.org/3dcitydb/building/5.0","description":"Indicates the high point used to calculate the value of the room height.","type":"core:Code","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (901, null, 'ADEOfAbstractBuilding', 1, 10, '{"identifier":"bldg:ADEOfAbstractBuilding","description":"ADEOfAbstractBuilding acts as a hook to define properties within an ADE that are to be added to AbstractBuilding.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (902, null, 'ADEOfAbstractBuildingSubdivision', 1, 10, '{"identifier":"bldg:ADEOfAbstractBuildingSubdivision","description":"ADEOfAbstractBuildingSubdivision acts as a hook to define properties within an ADE that are to be added to AbstractBuildingSubdivision.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (903, null, 'ADEOfBuilding', 1, 10, '{"identifier":"bldg:ADEOfBuilding","description":"ADEOfBuilding acts as a hook to define properties within an ADE that are to be added to a Building.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (904, null, 'ADEOfBuildingConstructiveElement', 1, 10, '{"identifier":"bldg:ADEOfBuildingConstructiveElement","description":"ADEOfBuildingConstructiveElement acts as a hook to define properties within an ADE that are to be added to a BuildingConstructiveElement.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (905, null, 'ADEOfBuildingFurniture', 1, 10, '{"identifier":"bldg:ADEOfBuildingFurniture","description":"ADEOfBuildingFurniture acts as a hook to define properties within an ADE that are to be added to a BuildingFurniture.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (906, null, 'ADEOfBuildingInstallation', 1, 10, '{"identifier":"bldg:ADEOfBuildingInstallation","description":"ADEOfBuildingInstallation acts as a hook to define properties within an ADE that are to be added to a BuildingInstallation.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (907, null, 'ADEOfBuildingPart', 1, 10, '{"identifier":"bldg:ADEOfBuildingPart","description":"ADEOfBuildingPart acts as a hook to define properties within an ADE that are to be added to a BuildingPart.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (908, null, 'ADEOfBuildingRoom', 1, 10, '{"identifier":"bldg:ADEOfBuildingRoom","description":"ADEOfBuildingRoom acts as a hook to define properties within an ADE that are to be added to a BuildingRoom.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (909, null, 'ADEOfBuildingUnit', 1, 10, '{"identifier":"bldg:ADEOfBuildingUnit","description":"ADEOfBuildingUnit acts as a hook to define properties within an ADE that are to be added to a BuildingUnit.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (910, null, 'ADEOfStorey', 1, 10, '{"identifier":"bldg:ADEOfStorey","description":"ADEOfStorey acts as a hook to define properties within an ADE that are to be added to a Storey.","table":"property"}');

-- Bridge Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1000, null, 'ADEOfAbstractBridge', 1, 11, '{"identifier":"brid:ADEOfAbstractBridge","description":"ADEOfAbstractBridge acts as a hook to define properties within an ADE that are to be added to AbstractBridge.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1001, null, 'ADEOfBridge', 1, 11, '{"identifier":"brid:ADEOfBridge","description":"ADEOfBridge acts as a hook to define properties within an ADE that are to be added to a Bridge.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1002, null, 'ADEOfBridgeConstructiveElement', 1, 11, '{"identifier":"brid:ADEOfBridgeConstructiveElement","description":"ADEOfBridgeConstructiveElement acts as a hook to define properties within an ADE that are to be added to a BridgeConstructiveElement.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1003, null, 'ADEOfBridgeFurniture', 1, 11, '{"identifier":"brid:ADEOfBridgeFurniture","description":"ADEOfBridgeFurniture acts as a hook to define properties within an ADE that are to be added to a BridgeFurniture.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1004, null, 'ADEOfBridgeInstallation', 1, 11, '{"identifier":"brid:ADEOfBridgeInstallation","description":"ADEOfBridgeInstallation acts as a hook to define properties within an ADE that are to be added to a BridgeInstallation.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1005, null, 'ADEOfBridgePart', 1, 11, '{"identifier":"brid:ADEOfBridgePart","description":"ADEOfBridgePart acts as a hook to define properties within an ADE that are to be added to a BridgePart.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1006, null, 'ADEOfBridgeRoom', 1, 11, '{"identifier":"brid:ADEOfBridgeRoom","description":"ADEOfBridgeRoom acts as a hook to define properties within an ADE that are to be added to a BridgeRoom.","table":"property"}');

-- CityObjectGroup Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1200, null, 'Role', 0, 13, '{"identifier":"grp:Role","description":"Role qualifies the function of a city object within the CityObjectGroup.","table":"property","properties":[{"name":"groupMember","namespace":"http://3dcitydb.org/3dcitydb/cityobjectgroup/5.0","description":"Relates to the city objects that are part of the CityObjectGroup.","type":"core:FeatureProperty","target":"core:AbstractCityObject","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"role","namespace":"http://3dcitydb.org/3dcitydb/cityobjectgroup/5.0","description":"Describes the role the city object plays within the CityObjectGroup.","type":"core:String","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1201, null, 'ADEOfCityObjectGroup', 1, 13, '{"identifier":"grp:ADEOfCityObjectGroup","description":"ADEOfCityObjectGroup acts as a hook to define properties within an ADE that are to be added to a CityObjectGroup.","table":"property"}');

-- Vegetation Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1300, null, 'ADEOfAbstractVegetationObject', 1, 14, '{"identifier":"veg:ADEOfAbstractVegetationObject","description":"ADEOfAbstractVegetationObject acts as a hook to define properties within an ADE that are to be added to AbstractVegetationObject.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1301, null, 'ADEOfPlantCover', 1, 14, '{"identifier":"veg:ADEOfPlantCover","description":"ADEOfPlantCover acts as a hook to define properties within an ADE that are to be added to a PlantCover.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1302, null, 'ADEOfSolitaryVegetationObject', 1, 14, '{"identifier":"veg:ADEOfSolitaryVegetationObject","description":"ADEOfSolitaryVegetationObject acts as a hook to define properties within an ADE that are to be added to a SolitaryVegetationObject.","table":"property"}');

-- Versioning Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1400, null, 'Transaction', 0, 15, '{"identifier":"vers:Transaction","description":"Transaction represents a modification of the city model by the creation, termination, or replacement of a specific city object. While the creation of a city object also marks its first object version, the termination marks the end of existence of a real world object and, hence, also terminates the final version of a city object. The replacement of a city object means that a specific version of it is replaced by a new version.","table":"property","properties":[{"name":"type","namespace":"http://3dcitydb.org/3dcitydb/versioning/5.0","description":"Indicates the specific type of the Transaction.","type":"core:String","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"oldFeature","namespace":"http://3dcitydb.org/3dcitydb/versioning/5.0","description":"Relates to the version of the city object prior to the Transaction.","type":"core:FeatureProperty","target":"core:AbstractFeatureWithLifespan","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}},{"name":"newFeature","namespace":"http://3dcitydb.org/3dcitydb/versioning/5.0","description":"Relates to the version of the city object subsequent to the Transaction.","type":"core:FeatureProperty","target":"core:AbstractFeatureWithLifespan","join":{"table":"property","fromColumn":"id","toColumn":"parent_id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1401, null, 'ADEOfVersion', 1, 15, '{"identifier":"vers:ADEOfVersion","description":"ADEOfVersion acts as a hook to define properties within an ADE that are to be added to a Version.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1402, null, 'ADEOfVersionTransition', 1, 15, '{"identifier":"vers:ADEOfVersionTransition","description":"ADEOfVersionTransition acts as a hook to define properties within an ADE that are to be added to a VersionTransition.","table":"property"}');

-- WaterBody Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1500, null, 'ADEOfAbstractWaterBoundarySurface', 1, 16, '{"identifier":"wtr:ADEOfAbstractWaterBoundarySurface","description":"ADEOfAbstractWaterBoundarySurface acts as a hook to define properties within an ADE that are to be added to AbstractWaterBoundarySurface.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1501, null, 'ADEOfWaterBody', 1, 16, '{"identifier":"wtr:ADEOfWaterBody","description":"ADEOfWaterBody acts as a hook to define properties within an ADE that are to be added to a WaterBody.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1502, null, 'ADEOfWaterGroundSurface', 1, 16, '{"identifier":"wtr:ADEOfWaterGroundSurface","description":"ADEOfWaterGroundSurface acts as a hook to define properties within an ADE that are to be added to a WaterGroundSurface.","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1503, null, 'ADEOfWaterSurface', 1, 16, '{"identifier":"wtr:ADEOfWaterSurface","description":"ADEOfWaterSurface acts as a hook to define properties within an ADE that are to be added to a WaterSurface.","table":"property"}');

-- CityFurniture Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1600, null, 'ADEOfCityFurniture', 1, 17, '{"identifier":"frn:ADEOfCityFurniture","description":"ADEOfCityFurniture acts as a hook to define properties within an ADE that are to be added to a CityFurniture.","table":"property"}');