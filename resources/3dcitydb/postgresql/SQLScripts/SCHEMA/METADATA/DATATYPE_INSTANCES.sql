DELETE FROM datatype;

-- Core Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (1, null, 'AppearanceProperty', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (2, null, 'Boolean', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (3, null, 'CityObjectRelation', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (4, null, 'Code', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (5, null, 'Double', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (6, null, 'DoubleList', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (7, null, 'ExternalReference', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (8, null, 'FeatureProperty', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (9, null, 'GeometryProperty', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (10, null, 'ImplicitGeometryProperty', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (11, null, 'Integer', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (12, null, 'Measure', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (13, null, 'MeasureOrNilReasonList', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (14, null, 'Occupancy', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (15, null, 'QualifiedArea', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (16, null, 'QualifiedVolume', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (17, null, 'String', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (18, null, 'StringOrRef', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (19, null, 'Timestamp', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (20, null, 'URI', 0, 1);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (21, null, 'AddressProperty', 0, 1);

-- Dynamizer Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (100, null, 'SensorConnection', 0, 2);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (101, null, 'TimeseriesComponent', 0, 2);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (102, null, 'TimePairValue', 0, 2);

-- Generics Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (200, null, 'GenericAttributeSet', 0, 3);

-- Construction Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (700, null, 'ConstructionEvent', 0, 8);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (701, null, 'Elevation', 0, 8);

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (702, null, 'Height', 0, 8);

-- CityObjectGroup Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (1200, null, 'Role', 0, 13);

-- Versioning Module --

INSERT INTO datatype (ID, SUPERTYPE_ID, TYPENAME, IS_ABSTRACT, NAMESPACE_ID)
VALUES (1400, null, 'Transaction', 0, 15);
















