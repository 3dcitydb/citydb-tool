WITH RECURSIVE FEATURE_HIERARCHY AS
  (SELECT NULL::bigint AS ID,
     ID AS FEATURE_ID,
     NULL::bigint AS PARENT_ID,
     NULL::integer AS DATATYPE_ID,
     NULL::integer AS NAMESPACE_ID,
     NULL::text AS NAME,
     NULL::bigint AS VAL_INT,
     NULL::double precision AS VAL_DOUBLE,
     NULL::text AS VAL_STRING,
     NULL::timestamp with time zone AS VAL_TIMESTAMP,
     NULL::text AS VAL_URI,
     NULL::text AS VAL_CODESPACE,
     NULL::text AS VAL_UOM,
     NULL::@JSON@ AS VAL_ARRAY,
     NULL::text AS VAL_LOD,
     NULL::bigint AS VAL_GEOMETRY_ID,
     NULL::bigint AS VAL_IMPLICITGEOM_ID,
     NULL::geometry AS VAL_IMPLICITGEOM_REFPOINT,
     NULL::bigint AS VAL_APPEARANCE_ID,
     NULL::bigint AS VAL_ADDRESS_ID,
     ID AS VAL_FEATURE_ID,
     1::integer AS VAL_RELATION_TYPE,
     NULL::text AS VAL_CONTENT,
     NULL::text AS VAL_CONTENT_MIME_TYPE,
     FALSE AS IS_CYCLE,
     ARRAY[]::bigint[] AS PATH
   FROM (SELECT ?::bigint AS ID) F
   UNION ALL SELECT
     P.ID,
     P.FEATURE_ID,
     P.PARENT_ID,
     P.DATATYPE_ID,
     P.NAMESPACE_ID,
     P.NAME,
     P.VAL_INT,
     P.VAL_DOUBLE,
     P.VAL_STRING,
     P.VAL_TIMESTAMP,
     P.VAL_URI,
     P.VAL_CODESPACE,
     P.VAL_UOM,
     P.VAL_ARRAY,
     P.VAL_LOD,
     P.VAL_GEOMETRY_ID,
     P.VAL_IMPLICITGEOM_ID,
     P.VAL_IMPLICITGEOM_REFPOINT,
     P.VAL_APPEARANCE_ID,
     P.VAL_ADDRESS_ID,
     P.VAL_FEATURE_ID,
     P.VAL_RELATION_TYPE,
     P.VAL_CONTENT,
     P.VAL_CONTENT_MIME_TYPE,
     P.ID = ANY(PATH),
     PATH || P.ID
   FROM @SCHEMA@.PROPERTY P
   INNER JOIN FEATURE_HIERARCHY H ON H.VAL_FEATURE_ID = P.FEATURE_ID AND H.VAL_RELATION_TYPE = 1
   WHERE NOT IS_CYCLE)
SELECT
  H.ID,
  H.FEATURE_ID,
  H.PARENT_ID,
  H.DATATYPE_ID,
  H.NAMESPACE_ID,
  H.NAME,
  H.VAL_INT,
  H.VAL_DOUBLE,
  H.VAL_STRING,
  H.VAL_TIMESTAMP,
  H.VAL_URI,
  H.VAL_CODESPACE,
  H.VAL_UOM,
  H.VAL_ARRAY,
  H.VAL_LOD,
  H.VAL_GEOMETRY_ID,
  H.VAL_IMPLICITGEOM_ID,
  H.VAL_IMPLICITGEOM_REFPOINT,
  H.VAL_APPEARANCE_ID,
  H.VAL_ADDRESS_ID,
  H.VAL_FEATURE_ID,
  H.VAL_RELATION_TYPE,
  H.VAL_CONTENT,
  H.VAL_CONTENT_MIME_TYPE
FROM FEATURE_HIERARCHY H
