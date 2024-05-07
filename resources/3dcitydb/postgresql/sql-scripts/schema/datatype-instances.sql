DELETE FROM datatype;

-- Core Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1, null, 'Undefined', 0, 1, '{"identifier":"core:Undefined","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (2, null, 'Boolean', 0, 1, '{"identifier":"core:Boolean","table":"property","value":{"column":"val_int","type":"boolean"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (3, null, 'Integer', 0, 1, '{"identifier":"core:Integer","table":"property","value":{"column":"val_int","type":"integer"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (4, null, 'Double', 0, 1, '{"identifier":"core:Double","table":"property","value":{"column":"val_double","type":"double"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (5, null, 'String', 0, 1, '{"identifier":"core:String","table":"property","value":{"column":"val_string","type":"string"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (6, null, 'URI', 0, 1, '{"identifier":"core:URI","table":"property","value":{"column":"val_uri","type":"uri"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (7, null, 'Timestamp', 0, 1, '{"identifier":"core:Timestamp","table":"property","value":{"column":"val_timestamp","type":"timestamp"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (8, null, 'AddressProperty', 0, 1, '{"identifier":"core:AddressProperty","table":"property","join":{"table":"address","fromColumn":"val_address_id","toColumn":"id"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (9, null, 'AppearanceProperty', 0, 1, '{"identifier":"core:AppearanceProperty","table":"property","join":{"table":"appearance","fromColumn":"val_appearance_id","toColumn":"id"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (10, null, 'FeatureProperty', 0, 1, '{"identifier":"core:FeatureProperty","table":"property","join":{"table":"feature","fromColumn":"val_feature_id","toColumn":"id","conditions":[{"column":"objectclass_id","value":"@target.objectclass_id@","type":"integer"}]}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (11, null, 'GeometryProperty', 0, 1, '{"identifier":"core:GeometryProperty","table":"property","properties":[{"name":"lod","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","value":{"column":"val_lod","type":"string"}}],"join":{"table":"geometry_data","fromColumn":"val_geometry_id","toColumn":"id"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (12, null, 'Reference', 0, 1, '{"identifier":"core:Reference","table":"property","value":{"column":"val_uri","type":"uri"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (13, null, 'CityObjectRelation', 0, 1, '{"identifier":"core:CityObjectRelation","table":"property","properties":[{"name":"relatedTo","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","type":"core:FeatureProperty","target":"core:AbstractCityObject","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"relationType","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","type":"core:Code","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (14, null, 'Code', 0, 1, '{"identifier":"core:Code","table":"property","value":{"column":"val_string","type":"string"},"properties":[{"name":"codeSpace","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","value":{"column":"val_codespace","type":"uri"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (15, null, 'ExternalReference', 0, 1, '{"identifier":"core:ExternalReference","table":"property","properties":[{"name":"targetResource","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","type":"core:URI"},{"name":"informationSystem","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","value":{"column":"val_codespace","type":"uri"}},{"name":"relationType","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","value":{"column":"val_string","type":"uri"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (16, null, 'ImplicitGeometryProperty', 0, 1, '{"identifier":"core:ImplicitGeometryProperty","table":"property","properties":[{"name":"transformationMatrix","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","value":{"column":"val_array","type":"doubleArray"}},{"name":"referencePoint","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","value":{"column":"val_implicitgeom_refpoint","type":"geometry"}}],"join":{"table":"implicit_geometry","fromColumn":"val_implicitgeom_id","toColumn":"id"}}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (17, null, 'Measure', 0, 1, '{"identifier":"core:Measure","table":"property","value":{"column":"val_double","type":"double"},"properties":[{"name":"uom","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","value":{"column":"val_uom","type":"string"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (18, null, 'MeasureOrNilReasonList', 0, 1, '{"identifier":"core:MeasureOrNilReasonList","table":"property","value":{"column":"val_array","type":"array","schema":{"items":{"type":["number","string"]}}},"properties":[{"name":"uom","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","value":{"column":"val_uom","type":"string"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (19, null, 'Occupancy', 0, 1, '{"identifier":"core:Occupancy","table":"property","value":{"property":0},"properties":[{"name":"numberOfOccupants","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","type":"core:Integer","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"interval","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","type":"core:Code","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"occupantType","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","type":"core:Code","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (20, null, 'QualifiedArea', 0, 1, '{"identifier":"core:QualifiedArea","table":"property","value":{"property":0},"properties":[{"name":"area","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","type":"core:Measure"},{"name":"typeOfArea","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","type":"core:Code"}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (21, null, 'QualifiedVolume', 0, 1, '{"identifier":"core:QualifiedVolume","table":"property","value":{"property":0},"properties":[{"name":"volume","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","type":"core:Measure"},{"name":"typeOfVolume","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","type":"core:Code"}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (22, null, 'StringOrRef', 0, 1, '{"identifier":"core:StringOrRef","table":"property","value":{"column":"val_string","type":"string"},"properties":[{"name":"href","namespace":"http://3dcitydb.org/3dcitydb/core/5.0","value":{"column":"val_uri","type":"uri"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (23, null, 'ADEOfAbstractCityObject', 1, 1, '{"identifier":"core:ADEOfAbstractCityObject","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (24, null, 'ADEOfAbstractDynamizer', 1, 1, '{"identifier":"core:ADEOfAbstractDynamizer","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (25, null, 'ADEOfAbstractFeature', 1, 1, '{"identifier":"core:ADEOfAbstractFeature","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (26, null, 'ADEOfAbstractFeatureWithLifespan', 1, 1, '{"identifier":"core:ADEOfAbstractFeatureWithLifespan","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (27, null, 'ADEOfAbstractLogicalSpace', 1, 1, '{"identifier":"core:ADEOfAbstractLogicalSpace","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (28, null, 'ADEOfAbstractOccupiedSpace', 1, 1, '{"identifier":"core:ADEOfAbstractOccupiedSpace","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (29, null, 'ADEOfAbstractPhysicalSpace', 1, 1, '{"identifier":"core:ADEOfAbstractPhysicalSpace","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (30, null, 'ADEOfAbstractPointCloud', 1, 1, '{"identifier":"core:ADEOfAbstractPointCloud","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (31, null, 'ADEOfAbstractSpace', 1, 1, '{"identifier":"core:ADEOfAbstractSpace","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (32, null, 'ADEOfAbstractSpaceBoundary', 1, 1, '{"identifier":"core:ADEOfAbstractSpaceBoundary","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (33, null, 'ADEOfAbstractThematicSurface', 1, 1, '{"identifier":"core:ADEOfAbstractThematicSurface","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (34, null, 'ADEOfAbstractUnoccupiedSpace', 1, 1, '{"identifier":"core:ADEOfAbstractUnoccupiedSpace","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (35, null, 'ADEOfAbstractVersion', 1, 1, '{"identifier":"core:ADEOfAbstractVersion","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (36, null, 'ADEOfAbstractVersionTransition', 1, 1, '{"identifier":"core:ADEOfAbstractVersionTransition","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (37, null, 'ADEOfCityModel', 1, 1, '{"identifier":"core:ADEOfCityModel","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (38, null, 'ADEOfClosureSurface', 1, 1, '{"identifier":"core:ADEOfClosureSurface","table":"property"}');

-- Dynamizer Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (100, null, 'SensorConnection', 0, 2, '{"identifier":"dyn:SensorConnection","table":"property","properties":[{"name":"connectionType","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:Code","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"observationProperty","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:String","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"uom","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:String","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"sensorID","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:String","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"sensorName","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:String","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"observationID","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:String","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"datastreamID","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:String","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"baseURL","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:URI","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"authType","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:Code","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"mqttServer","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:String","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"mqttTopic","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:String","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"linkToObservation","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:String","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"linkToSensorDescription","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:String","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"sensorLocation","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:FeatureProperty","target":"core:AbstractCityObject","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (101, null, 'TimeseriesComponent', 0, 2, '{"identifier":"dyn:TimeseriesComponent","table":"property","properties":[{"name":"repetitions","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:Integer","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"additionalGap","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:String","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"timeseries","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:FeatureProperty","target":"dyn:AbstractTimeseries","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (102, null, 'TimeValuePair', 0, 2, '{"identifier":"dyn:TimeValuePair","table":"property","properties":[{"name":"timestamp","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:Timestamp","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"intValue","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:Integer","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"doubleValue","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:Double","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"stringValue","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:String","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"geometryValue","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:GeometryProperty","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"uriValue","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:URI","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"boolValue","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:Boolean","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"implicitGeometryValue","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:ImplicitGeometryProperty","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"appearanceValue","namespace":"http://3dcitydb.org/3dcitydb/dynamizer/5.0","type":"core:AppearanceProperty","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (103, null, 'ADEOfAbstractAtomicTimeseries', 1, 2, '{"identifier":"dyn:ADEOfAbstractAtomicTimeseries","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (104, null, 'ADEOfAbstractTimeseries', 1, 2, '{"identifier":"dyn:ADEOfAbstractTimeseries","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (105, null, 'ADEOfCompositeTimeseries', 1, 2, '{"identifier":"dyn:ADEOfCompositeTimeseries","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (106, null, 'ADEOfDynamizer', 1, 2, '{"identifier":"dyn:ADEOfDynamizer","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (107, null, 'ADEOfGenericTimeseries', 1, 2, '{"identifier":"dyn:ADEOfGenericTimeseries","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (108, null, 'ADEOfStandardFileTimeseries', 1, 2, '{"identifier":"dyn:ADEOfStandardFileTimeseries","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (109, null, 'ADEOfTabulatedFileTimeseries', 1, 2, '{"identifier":"dyn:ADEOfTabulatedFileTimeseries","table":"property"}');

-- Generics Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (200, null, 'GenericAttributeSet', 0, 3, '{"identifier":"gen:GenericAttributeSet","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (201, null, 'ADEOfGenericLogicalSpace', 1, 3, '{"identifier":"gen:ADEOfGenericLogicalSpace","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (202, null, 'ADEOfGenericOccupiedSpace', 1, 3, '{"identifier":"gen:ADEOfGenericOccupiedSpace","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (203, null, 'ADEOfGenericThematicSurface', 1, 3, '{"identifier":"gen:ADEOfGenericThematicSurface","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (204, null, 'ADEOfGenericUnoccupiedSpace', 1, 3, '{"identifier":"gen:ADEOfGenericUnoccupiedSpace","table":"property"}');

-- LandUse Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (300, null, 'ADEOfLandUse', 1, 4, '{"identifier":"luse:ADEOfLandUse","table":"property"}');

-- PointCloud Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (400, null, 'ADEOfPointCloud', 1, 5, '{"identifier":"pcl:ADEOfPointCloud","table":"property"}');

-- Relief Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (500, null, 'ADEOfAbstractReliefComponent', 1, 6, '{"identifier":"dem:ADEOfAbstractReliefComponent","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (501, null, 'ADEOfBreaklineRelief', 1, 6, '{"identifier":"dem:ADEOfBreaklineRelief","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (502, null, 'ADEOfMassPointRelief', 1, 6, '{"identifier":"dem:ADEOfMassPointRelief","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (503, null, 'ADEOfRasterRelief', 1, 6, '{"identifier":"dem:ADEOfRasterRelief","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (504, null, 'ADEOfReliefFeature', 1, 6, '{"identifier":"dem:ADEOfReliefFeature","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (505, null, 'ADEOfTINRelief', 1, 6, '{"identifier":"dem:ADEOfTINRelief","table":"property"}');

-- Transportation Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (600, null, 'ADEOfAbstractTransportationSpace', 1, 7, '{"identifier":"tran:ADEOfAbstractTransportationSpace","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (601, null, 'ADEOfAuxiliaryTrafficArea', 1, 7, '{"identifier":"tran:ADEOfAuxiliaryTrafficArea","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (602, null, 'ADEOfAuxiliaryTrafficSpace', 1, 7, '{"identifier":"tran:ADEOfAuxiliaryTrafficSpace","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (603, null, 'ADEOfClearanceSpace', 1, 7, '{"identifier":"tran:ADEOfClearanceSpace","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (604, null, 'ADEOfHole', 1, 7, '{"identifier":"tran:ADEOfHole","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (605, null, 'ADEOfHoleSurface', 1, 7, '{"identifier":"tran:ADEOfHoleSurface","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (606, null, 'ADEOfIntersection', 1, 7, '{"identifier":"tran:ADEOfIntersection","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (607, null, 'ADEOfMarking', 1, 7, '{"identifier":"tran:ADEOfMarking","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (608, null, 'ADEOfRailway', 1, 7, '{"identifier":"tran:ADEOfRailway","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (609, null, 'ADEOfRoad', 1, 7, '{"identifier":"tran:ADEOfRoad","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (610, null, 'ADEOfSection', 1, 7, '{"identifier":"tran:ADEOfSection","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (611, null, 'ADEOfSquare', 1, 7, '{"identifier":"tran:ADEOfSquare","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (612, null, 'ADEOfTrack', 1, 7, '{"identifier":"tran:ADEOfTrack","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (613, null, 'ADEOfTrafficArea', 1, 7, '{"identifier":"tran:ADEOfTrafficArea","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (614, null, 'ADEOfTrafficSpace', 1, 7, '{"identifier":"tran:ADEOfTrafficSpace","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (615, null, 'ADEOfWaterway', 1, 7, '{"identifier":"tran:ADEOfWaterway","table":"property"}');

-- Construction Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (700, null, 'ConstructionEvent', 0, 8, '{"identifier":"con:ConstructionEvent","table":"property","value":{"property":0},"properties":[{"name":"event","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","type":"core:Code","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"dateOfEvent","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","type":"core:Timestamp","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"description","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","type":"core:String","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (701, null, 'Elevation', 0, 8, '{"identifier":"con:Elevation","table":"property","properties":[{"name":"elevationValue","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","value":{"column":"val_array","type":"doubleArray"}},{"name":"elevationReference","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","type":"core:Code"}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (702, null, 'Height', 0, 8, '{"identifier":"con:Height","table":"property","value":{"property":0},"properties":[{"name":"value","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","type":"core:Measure","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"status","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","type":"core:String","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"lowReference","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","type":"core:Code","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"highReference","namespace":"http://3dcitydb.org/3dcitydb/construction/5.0","type":"core:Code","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (703, null, 'ADEOfAbstractConstruction', 1, 8, '{"identifier":"con:ADEOfAbstractConstruction","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (704, null, 'ADEOfAbstractConstructionSurface', 1, 8, '{"identifier":"con:ADEOfAbstractConstructionSurface","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (705, null, 'ADEOfAbstractConstructiveElement', 1, 8, '{"identifier":"con:ADEOfAbstractConstructiveElement","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (706, null, 'ADEOfAbstractFillingElement', 1, 8, '{"identifier":"con:ADEOfAbstractFillingElement","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (707, null, 'ADEOfAbstractFillingSurface', 1, 8, '{"identifier":"con:ADEOfAbstractFillingSurface","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (708, null, 'ADEOfAbstractFurniture', 1, 8, '{"identifier":"con:ADEOfAbstractFurniture","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (709, null, 'ADEOfAbstractInstallation', 1, 8, '{"identifier":"con:ADEOfAbstractInstallation","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (710, null, 'ADEOfCeilingSurface', 1, 8, '{"identifier":"con:ADEOfCeilingSurface","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (711, null, 'ADEOfDoor', 1, 8, '{"identifier":"con:ADEOfDoor","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (712, null, 'ADEOfDoorSurface', 1, 8, '{"identifier":"con:ADEOfDoorSurface","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (713, null, 'ADEOfFloorSurface', 1, 8, '{"identifier":"con:ADEOfFloorSurface","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (714, null, 'ADEOfGroundSurface', 1, 8, '{"identifier":"con:ADEOfGroundSurface","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (715, null, 'ADEOfInteriorWallSurface', 1, 8, '{"identifier":"con:ADEOfInteriorWallSurface","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (716, null, 'ADEOfOtherConstruction', 1, 8, '{"identifier":"con:ADEOfOtherConstruction","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (717, null, 'ADEOfOuterCeilingSurface', 1, 8, '{"identifier":"con:ADEOfOuterCeilingSurface","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (718, null, 'ADEOfOuterFloorSurface', 1, 8, '{"identifier":"con:ADEOfOuterFloorSurface","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (719, null, 'ADEOfRoofSurface', 1, 8, '{"identifier":"con:ADEOfRoofSurface","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (720, null, 'ADEOfWallSurface', 1, 8, '{"identifier":"con:ADEOfWallSurface","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (721, null, 'ADEOfWindow', 1, 8, '{"identifier":"con:ADEOfWindow","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (722, null, 'ADEOfWindowSurface', 1, 8, '{"identifier":"con:ADEOfWindowSurface","table":"property"}');

-- Tunnel Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (800, null, 'ADEOfAbstractTunnel', 1, 9, '{"identifier":"tun:ADEOfAbstractTunnel","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (801, null, 'ADEOfHollowSpace', 1, 9, '{"identifier":"tun:ADEOfHollowSpace","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (802, null, 'ADEOfTunnel', 1, 9, '{"identifier":"tun:ADEOfTunnel","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (803, null, 'ADEOfTunnelConstructiveElement', 1, 9, '{"identifier":"tun:ADEOfTunnelConstructiveElement","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (804, null, 'ADEOfTunnelFurniture', 1, 9, '{"identifier":"tun:ADEOfTunnelFurniture","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (805, null, 'ADEOfTunnelInstallation', 1, 9, '{"identifier":"tun:ADEOfTunnelInstallation","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (806, null, 'ADEOfTunnelPart', 1, 9, '{"identifier":"tun:ADEOfTunnelPart","table":"property"}');

-- Building Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (900, null, 'RoomHeight', 0, 10, '{"identifier":"bldg:RoomHeight","table":"property","value":{"property":0},"properties":[{"name":"value","namespace":"http://3dcitydb.org/3dcitydb/building/5.0","type":"core:Measure","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"status","namespace":"http://3dcitydb.org/3dcitydb/building/5.0","type":"core:String","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"lowReference","namespace":"http://3dcitydb.org/3dcitydb/building/5.0","type":"core:Code","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"highReference","namespace":"http://3dcitydb.org/3dcitydb/building/5.0","type":"core:Code","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (901, null, 'ADEOfAbstractBuilding', 1, 10, '{"identifier":"bldg:ADEOfAbstractBuilding","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (902, null, 'ADEOfAbstractBuildingSubdivision', 1, 10, '{"identifier":"bldg:ADEOfAbstractBuildingSubdivision","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (903, null, 'ADEOfBuilding', 1, 10, '{"identifier":"bldg:ADEOfBuilding","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (904, null, 'ADEOfBuildingConstructiveElement', 1, 10, '{"identifier":"bldg:ADEOfBuildingConstructiveElement","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (905, null, 'ADEOfBuildingFurniture', 1, 10, '{"identifier":"bldg:ADEOfBuildingFurniture","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (906, null, 'ADEOfBuildingInstallation', 1, 10, '{"identifier":"bldg:ADEOfBuildingInstallation","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (907, null, 'ADEOfBuildingPart', 1, 10, '{"identifier":"bldg:ADEOfBuildingPart","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (908, null, 'ADEOfBuildingRoom', 1, 10, '{"identifier":"bldg:ADEOfBuildingRoom","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (909, null, 'ADEOfBuildingUnit', 1, 10, '{"identifier":"bldg:ADEOfBuildingUnit","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (910, null, 'ADEOfStorey', 1, 10, '{"identifier":"bldg:ADEOfStorey","table":"property"}');

-- Bridge Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1000, null, 'ADEOfAbstractBridge', 1, 11, '{"identifier":"brid:ADEOfAbstractBridge","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1001, null, 'ADEOfBridge', 1, 11, '{"identifier":"brid:ADEOfBridge","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1002, null, 'ADEOfBridgeConstructiveElement', 1, 11, '{"identifier":"brid:ADEOfBridgeConstructiveElement","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1003, null, 'ADEOfBridgeFurniture', 1, 11, '{"identifier":"brid:ADEOfBridgeFurniture","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1004, null, 'ADEOfBridgeInstallation', 1, 11, '{"identifier":"brid:ADEOfBridgeInstallation","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1005, null, 'ADEOfBridgePart', 1, 11, '{"identifier":"brid:ADEOfBridgePart","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1006, null, 'ADEOfBridgeRoom', 1, 11, '{"identifier":"brid:ADEOfBridgeRoom","table":"property"}');

-- CityObjectGroup Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1200, null, 'Role', 0, 13, '{"identifier":"grp:Role","table":"property","properties":[{"name":"groupMember","namespace":"http://3dcitydb.org/3dcitydb/cityobjectgroup/5.0","type":"core:FeatureProperty","target":"core:AbstractCityObject","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"role","namespace":"http://3dcitydb.org/3dcitydb/cityobjectgroup/5.0","type":"core:String","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1201, null, 'ADEOfCityObjectGroup', 1, 13, '{"identifier":"grp:ADEOfCityObjectGroup","table":"property"}');

-- Vegetation Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1300, null, 'ADEOfAbstractVegetationObject', 1, 14, '{"identifier":"veg:ADEOfAbstractVegetationObject","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1301, null, 'ADEOfPlantCover', 1, 14, '{"identifier":"veg:ADEOfPlantCover","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1302, null, 'ADEOfSolitaryVegetationObject', 1, 14, '{"identifier":"veg:ADEOfSolitaryVegetationObject","table":"property"}');

-- Versioning Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1400, null, 'Transaction', 0, 15, '{"identifier":"vers:Transaction","table":"property","properties":[{"name":"type","namespace":"http://3dcitydb.org/3dcitydb/versioning/5.0","type":"core:String","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"oldFeature","namespace":"http://3dcitydb.org/3dcitydb/versioning/5.0","type":"core:FeatureProperty","target":"core:AbstractFeatureWithLifespan","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}},{"name":"newFeature","namespace":"http://3dcitydb.org/3dcitydb/versioning/5.0","type":"core:FeatureProperty","target":"core:AbstractFeatureWithLifespan","join":{"table":"property","fromColumn":"parent_id","toColumn":"id"}}]}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1401, null, 'ADEOfVersion', 1, 15, '{"identifier":"vers:ADEOfVersion","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1402, null, 'ADEOfVersionTransition', 1, 15, '{"identifier":"vers:ADEOfVersionTransition","table":"property"}');

-- WaterBody Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1500, null, 'ADEOfAbstractWaterBoundarySurface', 1, 16, '{"identifier":"wtr:ADEOfAbstractWaterBoundarySurface","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1501, null, 'ADEOfWaterBody', 1, 16, '{"identifier":"wtr:ADEOfWaterBody","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1502, null, 'ADEOfWaterGroundSurface', 1, 16, '{"identifier":"wtr:ADEOfWaterGroundSurface","table":"property"}');

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1503, null, 'ADEOfWaterSurface', 1, 16, '{"identifier":"wtr:ADEOfWaterSurface","table":"property"}');

-- CityFurniture Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID, SCHEMA)
VALUES (1600, null, 'ADEOfCityFurniture', 1, 17, '{"identifier":"frn:ADEOfCityFurniture","table":"property"}');