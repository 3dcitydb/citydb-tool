package org.citydb.io.ifc.reader;

import org.bimserver.emf.IfcModelInterface;
import org.bimserver.models.ifc4.*;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class PropertyProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PropertyProcessor.class);

    private final IfcModelInterface model;
    private final boolean noProperties;
    private final boolean noGenericAttributeSets;
    private final boolean psetNamesAsPrefixes;
    private final Map<IfcObject, List<IfcPropertySetDefinitionSelect>> propertySetMap = new HashMap<>();

    public PropertyProcessor(IfcModelInterface model, boolean noProperties,
                             boolean noGenericAttributeSets, boolean psetNamesAsPrefixes) {
        this.model = model;
        this.noProperties = noProperties;
        this.noGenericAttributeSets = noGenericAttributeSets;
        this.psetNamesAsPrefixes = psetNamesAsPrefixes;
    }

    public void buildPropertySetMap() {
        List<IfcRelDefinesByProperties> rels = model.getAll(IfcRelDefinesByProperties.class);
        for (IfcRelDefinesByProperties rel : rels) {
            IfcPropertySetDefinitionSelect propDef = rel.getRelatingPropertyDefinition();
            if (propDef == null) continue;

            for (IfcObjectDefinition obj : rel.getRelatedObjects()) {
                if (obj instanceof IfcObject) {
                    propertySetMap.computeIfAbsent((IfcObject) obj, k -> new ArrayList<>()).add(propDef);
                }
            }
        }

        List<IfcRelDefinesByType> typeRels = model.getAll(IfcRelDefinesByType.class);
        for (IfcRelDefinesByType rel : typeRels) {
            IfcTypeObject typeObj = rel.getRelatingType();
            if (typeObj == null || !typeObj.isSetHasPropertySets()) continue;

            for (IfcObject obj : rel.getRelatedObjects()) {
                if (obj != null) {
                    for (IfcPropertySetDefinition setDef : typeObj.getHasPropertySets()) {
                        propertySetMap.computeIfAbsent(obj, k -> new ArrayList<>()).add(setDef);
                    }
                }
            }
        }

        logger.info("Property set map: {} elements with properties (from {} instance rels, {} type rels)",
                propertySetMap.size(), rels.size(), typeRels.size());
    }

    public void addProperties(Feature feature, IfcRoot ifcElement) {
        if (noProperties) return;
        if (!(ifcElement instanceof IfcObject ifcObject)) return;

        List<IfcPropertySetDefinitionSelect> propDefs = propertySetMap.get(ifcObject);
        if (propDefs == null || propDefs.isEmpty()) return;

        for (IfcPropertySetDefinitionSelect propDef : propDefs) {
            if (propDef instanceof IfcPropertySet) {
                addPropertySet(feature, (IfcPropertySet) propDef);
            } else if (propDef instanceof IfcElementQuantity) {
                addElementQuantity(feature, (IfcElementQuantity) propDef);
            } else if (propDef instanceof IfcWindowLiningProperties) {
                addWindowLiningProperties(feature, (IfcWindowLiningProperties) propDef);
            } else if (propDef instanceof IfcWindowPanelProperties) {
                addWindowPanelProperties(feature, (IfcWindowPanelProperties) propDef);
            } else if (propDef instanceof IfcDoorLiningProperties) {
                addDoorLiningProperties(feature, (IfcDoorLiningProperties) propDef);
            } else if (propDef instanceof IfcDoorPanelProperties) {
                addDoorPanelProperties(feature, (IfcDoorPanelProperties) propDef);
            }
        }
    }

    private void addPropertySet(Feature feature, IfcPropertySet pset) {
        String psetName = pset.getName() != null ? pset.getName() : "UnnamedPropertySet";

        if (noGenericAttributeSets) {
            for (IfcProperty prop : pset.getHasProperties()) {
                String propName = prop.getName();
                if (propName == null || propName.equals("id")) continue;
                Attribute attr = convertProperty(
                        psetNamesAsPrefixes ? "[" + psetName + "]" + propName : propName, prop);
                if (attr != null) {
                    feature.addAttribute(attr);
                }
            }
        } else {
            Attribute psetAttribute = Attribute.of(Name.of(psetName, Namespaces.GENERICS), DataType.GENERIC_ATTRIBUTE_SET);
            boolean hasChildren = false;
            for (IfcProperty prop : pset.getHasProperties()) {
                String propName = prop.getName();
                if (propName == null || propName.equals("id")) continue;
                Attribute attr = convertProperty(propName, prop);
                if (attr != null) {
                    psetAttribute.addProperty(attr);
                    hasChildren = true;
                }
            }
            if (hasChildren) {
                feature.addAttribute(psetAttribute);
            }
        }
    }

    private Attribute convertProperty(String propName, IfcProperty prop) {
        if (prop instanceof IfcPropertySingleValue sv) {
            IfcValue value = sv.getNominalValue();
            if (value != null) {
                return convertIfcValue(propName, value);
            }
        } else if (prop instanceof IfcPropertyEnumeratedValue ev) {
            if (ev.isSetEnumerationValues()) {
                StringBuilder sb = new StringBuilder();
                for (IfcValue val : ev.getEnumerationValues()) {
                    if (!sb.isEmpty()) sb.append(", ");
                    sb.append(ifcValueToString(val));
                }
                return Attribute.of(Name.of(propName, Namespaces.GENERICS), DataType.STRING)
                        .setStringValue(sb.toString());
            }
        } else if (prop instanceof IfcPropertyBoundedValue bv) {
            IfcValue upper = bv.getUpperBoundValue();
            IfcValue lower = bv.getLowerBoundValue();
            if (upper != null) {
                return convertIfcValue(propName, upper);
            } else if (lower != null) {
                return convertIfcValue(propName, lower);
            }
        } else if (prop instanceof IfcPropertyListValue lv) {
            if (lv.isSetListValues()) {
                StringBuilder sb = new StringBuilder();
                for (IfcValue val : lv.getListValues()) {
                    if (!sb.isEmpty()) sb.append(", ");
                    sb.append(ifcValueToString(val));
                }
                return Attribute.of(Name.of(propName, Namespaces.GENERICS), DataType.STRING)
                        .setStringValue(sb.toString());
            }
        } else if (prop instanceof IfcPropertyTableValue tv) {
            StringBuilder sb = new StringBuilder();
            if (tv.isSetDefiningValues()) {
                for (IfcValue val : tv.getDefiningValues()) {
                    if (!sb.isEmpty()) sb.append(", ");
                    sb.append(ifcValueToString(val));
                }
            }
            if (!sb.isEmpty()) {
                return Attribute.of(Name.of(propName, Namespaces.GENERICS), DataType.STRING)
                        .setStringValue(sb.toString());
            }
        } else if (prop instanceof IfcPropertyReferenceValue rv) {
            if (rv.getPropertyReference() != null) {
                return Attribute.of(Name.of(propName, Namespaces.GENERICS), DataType.STRING)
                        .setStringValue(rv.getPropertyReference().toString());
            }
        }
        return null;
    }

    private void addElementQuantity(Feature feature, IfcElementQuantity eq) {
        String qtoName = eq.getName() != null ? eq.getName() : "UnnamedQuantitySet";

        if (noGenericAttributeSets) {
            for (IfcPhysicalQuantity qty : eq.getQuantities()) {
                String qtyName = qty.getName();
                if (qtyName == null) continue;
                Attribute attr = convertQuantity(
                        psetNamesAsPrefixes ? "[" + qtoName + "]" + qtyName : qtyName, qty);
                if (attr != null) {
                    feature.addAttribute(attr);
                }
            }
        } else {
            Attribute qtoAttribute = Attribute.of(Name.of(qtoName, Namespaces.GENERICS), DataType.GENERIC_ATTRIBUTE_SET);
            boolean hasChildren = false;
            for (IfcPhysicalQuantity qty : eq.getQuantities()) {
                String qtyName = qty.getName();
                if (qtyName == null) continue;
                Attribute attr = convertQuantity(qtyName, qty);
                if (attr != null) {
                    qtoAttribute.addProperty(attr);
                    hasChildren = true;
                }
            }
            if (hasChildren) {
                feature.addAttribute(qtoAttribute);
            }
        }
    }

    private Attribute convertQuantity(String qtyName, IfcPhysicalQuantity qty) {
        if (qty instanceof IfcQuantityLength ql) {
            return Attribute.of(Name.of(qtyName, Namespaces.GENERICS), DataType.DOUBLE)
                    .setDoubleValue(ql.getLengthValue());
        } else if (qty instanceof IfcQuantityArea qa) {
            return Attribute.of(Name.of(qtyName, Namespaces.GENERICS), DataType.DOUBLE)
                    .setDoubleValue(qa.getAreaValue());
        } else if (qty instanceof IfcQuantityVolume qv) {
            return Attribute.of(Name.of(qtyName, Namespaces.GENERICS), DataType.DOUBLE)
                    .setDoubleValue(qv.getVolumeValue());
        } else if (qty instanceof IfcQuantityCount qc) {
            return Attribute.of(Name.of(qtyName, Namespaces.GENERICS), DataType.DOUBLE)
                    .setDoubleValue(qc.getCountValue());
        } else if (qty instanceof IfcQuantityWeight qw) {
            return Attribute.of(Name.of(qtyName, Namespaces.GENERICS), DataType.DOUBLE)
                    .setDoubleValue(qw.getWeightValue());
        } else if (qty instanceof IfcQuantityTime qt) {
            return Attribute.of(Name.of(qtyName, Namespaces.GENERICS), DataType.DOUBLE)
                    .setDoubleValue(qt.getTimeValue());
        }
        return null;
    }

    private void addWindowLiningProperties(Feature feature, IfcWindowLiningProperties props) {
        String name = props.getName() != null ? props.getName() : "Fenster Linien-Sachmerkmale";
        Attribute psetAttr = Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.GENERIC_ATTRIBUTE_SET);
        boolean hasChildren = false;
        if (props.isSetLiningDepth()) { psetAttr.addProperty(doubleAttr("LiningDepth", props.getLiningDepth())); hasChildren = true; }
        if (props.isSetLiningThickness()) { psetAttr.addProperty(doubleAttr("LiningThickness", props.getLiningThickness())); hasChildren = true; }
        if (props.isSetTransomThickness()) { psetAttr.addProperty(doubleAttr("TransomThickness", props.getTransomThickness())); hasChildren = true; }
        if (props.isSetMullionThickness()) { psetAttr.addProperty(doubleAttr("MullionThickness", props.getMullionThickness())); hasChildren = true; }
        if (props.isSetFirstTransomOffset()) { psetAttr.addProperty(doubleAttr("FirstTransomOffset", props.getFirstTransomOffset())); hasChildren = true; }
        if (props.isSetSecondTransomOffset()) { psetAttr.addProperty(doubleAttr("SecondTransomOffset", props.getSecondTransomOffset())); hasChildren = true; }
        if (props.isSetFirstMullionOffset()) { psetAttr.addProperty(doubleAttr("FirstMullionOffset", props.getFirstMullionOffset())); hasChildren = true; }
        if (props.isSetSecondMullionOffset()) { psetAttr.addProperty(doubleAttr("SecondMullionOffset", props.getSecondMullionOffset())); hasChildren = true; }
        if (props.isSetLiningOffset()) { psetAttr.addProperty(doubleAttr("LiningOffset", props.getLiningOffset())); hasChildren = true; }
        if (props.isSetLiningToPanelOffsetX()) { psetAttr.addProperty(doubleAttr("LiningToPanelOffsetX", props.getLiningToPanelOffsetX())); hasChildren = true; }
        if (props.isSetLiningToPanelOffsetY()) { psetAttr.addProperty(doubleAttr("LiningToPanelOffsetY", props.getLiningToPanelOffsetY())); hasChildren = true; }
        if (hasChildren) feature.addAttribute(psetAttr);
    }

    private void addWindowPanelProperties(Feature feature, IfcWindowPanelProperties props) {
        String name = props.getName() != null ? props.getName() : "Fenster Flügel-Sachmerkmale";
        Attribute psetAttr = Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.GENERIC_ATTRIBUTE_SET);
        boolean hasChildren = false;
        if (props.getOperationType() != IfcWindowPanelOperationEnum.NULL) {
            psetAttr.addProperty(stringAttr("OperationType", props.getOperationType().getLiteral()));
            hasChildren = true;
        }
        if (props.getPanelPosition() != IfcWindowPanelPositionEnum.NULL) {
            psetAttr.addProperty(stringAttr("PanelPosition", props.getPanelPosition().getLiteral()));
            hasChildren = true;
        }
        if (props.isSetFrameDepth()) { psetAttr.addProperty(doubleAttr("FrameDepth", props.getFrameDepth())); hasChildren = true; }
        if (props.isSetFrameThickness()) { psetAttr.addProperty(doubleAttr("FrameThickness", props.getFrameThickness())); hasChildren = true; }
        if (hasChildren) feature.addAttribute(psetAttr);
    }

    private void addDoorLiningProperties(Feature feature, IfcDoorLiningProperties props) {
        String name = props.getName() != null ? props.getName() : "Tür Linien-Sachmerkmale";
        Attribute psetAttr = Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.GENERIC_ATTRIBUTE_SET);
        boolean hasChildren = false;
        if (props.isSetLiningDepth()) { psetAttr.addProperty(doubleAttr("LiningDepth", props.getLiningDepth())); hasChildren = true; }
        if (props.isSetLiningThickness()) { psetAttr.addProperty(doubleAttr("LiningThickness", props.getLiningThickness())); hasChildren = true; }
        if (props.isSetThresholdDepth()) { psetAttr.addProperty(doubleAttr("ThresholdDepth", props.getThresholdDepth())); hasChildren = true; }
        if (props.isSetThresholdThickness()) { psetAttr.addProperty(doubleAttr("ThresholdThickness", props.getThresholdThickness())); hasChildren = true; }
        if (props.isSetTransomThickness()) { psetAttr.addProperty(doubleAttr("TransomThickness", props.getTransomThickness())); hasChildren = true; }
        if (props.isSetTransomOffset()) { psetAttr.addProperty(doubleAttr("TransomOffset", props.getTransomOffset())); hasChildren = true; }
        if (props.isSetLiningOffset()) { psetAttr.addProperty(doubleAttr("LiningOffset", props.getLiningOffset())); hasChildren = true; }
        if (props.isSetThresholdOffset()) { psetAttr.addProperty(doubleAttr("ThresholdOffset", props.getThresholdOffset())); hasChildren = true; }
        if (props.isSetCasingThickness()) { psetAttr.addProperty(doubleAttr("CasingThickness", props.getCasingThickness())); hasChildren = true; }
        if (props.isSetCasingDepth()) { psetAttr.addProperty(doubleAttr("CasingDepth", props.getCasingDepth())); hasChildren = true; }
        if (props.isSetLiningToPanelOffsetX()) { psetAttr.addProperty(doubleAttr("LiningToPanelOffsetX", props.getLiningToPanelOffsetX())); hasChildren = true; }
        if (props.isSetLiningToPanelOffsetY()) { psetAttr.addProperty(doubleAttr("LiningToPanelOffsetY", props.getLiningToPanelOffsetY())); hasChildren = true; }
        if (hasChildren) feature.addAttribute(psetAttr);
    }

    private void addDoorPanelProperties(Feature feature, IfcDoorPanelProperties props) {
        String name = props.getName() != null ? props.getName() : "Türblatt-Sachmerkmale";
        Attribute psetAttr = Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.GENERIC_ATTRIBUTE_SET);
        boolean hasChildren = false;
        if (props.isSetPanelDepth()) { psetAttr.addProperty(doubleAttr("PanelDepth", props.getPanelDepth())); hasChildren = true; }
        if (props.getPanelOperation() != IfcDoorPanelOperationEnum.NULL) {
            psetAttr.addProperty(stringAttr("PanelOperation", props.getPanelOperation().getLiteral()));
            hasChildren = true;
        }
        if (props.isSetPanelWidth()) { psetAttr.addProperty(doubleAttr("PanelWidth", props.getPanelWidth())); hasChildren = true; }
        if (props.getPanelPosition() != IfcDoorPanelPositionEnum.NULL) {
            psetAttr.addProperty(stringAttr("PanelPosition", props.getPanelPosition().getLiteral()));
            hasChildren = true;
        }
        if (hasChildren) feature.addAttribute(psetAttr);
    }

    private Attribute doubleAttr(String name, double value) {
        return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.DOUBLE).setDoubleValue(value);
    }

    private Attribute stringAttr(String name, String value) {
        return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.STRING).setStringValue(value);
    }

    private Attribute convertIfcValue(String name, IfcValue value) {
        if (value instanceof IfcBoolean bv) {
            Tristate ts = bv.getWrappedValue();
            return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.INTEGER)
                    .setIntValue(ts == Tristate.TRUE ? 1 : 0);
        } else if (value instanceof IfcLogical lv) {
            Tristate ts = lv.getWrappedValue();
            return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.INTEGER)
                    .setIntValue(ts == Tristate.TRUE ? 1 : 0);
        } else if (value instanceof IfcInteger iv) {
            return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.INTEGER)
                    .setIntValue((int) iv.getWrappedValue());
        } else if (value instanceof IfcReal rv) {
            return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.DOUBLE)
                    .setDoubleValue(rv.getWrappedValue());
        } else if (value instanceof IfcLabel lv) {
            String s = lv.getWrappedValue();
            return s != null ? Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.STRING)
                    .setStringValue(s) : null;
        } else if (value instanceof IfcText tv) {
            String s = tv.getWrappedValue();
            return s != null ? Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.STRING)
                    .setStringValue(s) : null;
        } else if (value instanceof IfcIdentifier idv) {
            String s = idv.getWrappedValue();
            return s != null ? Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.STRING)
                    .setStringValue(s) : null;
        } else if (value instanceof IfcLengthMeasure mv) {
            return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.DOUBLE)
                    .setDoubleValue(mv.getWrappedValue());
        } else if (value instanceof IfcAreaMeasure mv) {
            return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.DOUBLE)
                    .setDoubleValue(mv.getWrappedValue());
        } else if (value instanceof IfcVolumeMeasure mv) {
            return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.DOUBLE)
                    .setDoubleValue(mv.getWrappedValue());
        } else if (value instanceof IfcCountMeasure mv) {
            return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.DOUBLE)
                    .setDoubleValue(mv.getWrappedValue());
        } else if (value instanceof IfcPlaneAngleMeasure mv) {
            return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.DOUBLE)
                    .setDoubleValue(mv.getWrappedValue());
        } else if (value instanceof IfcMassMeasure mv) {
            return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.DOUBLE)
                    .setDoubleValue(mv.getWrappedValue());
        } else if (value instanceof IfcPowerMeasure mv) {
            return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.DOUBLE)
                    .setDoubleValue(mv.getWrappedValue());
        } else if (value instanceof IfcThermalTransmittanceMeasure mv) {
            return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.DOUBLE)
                    .setDoubleValue(mv.getWrappedValue());
        } else if (value instanceof IfcThermodynamicTemperatureMeasure mv) {
            return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.DOUBLE)
                    .setDoubleValue(mv.getWrappedValue());
        } else if (value instanceof IfcMeasureValue) {
            // Generic fallback using EMF reflective API (works under JPMS)
            try {
                EStructuralFeature feature = ((EObject) value).eClass()
                        .getEStructuralFeature("wrappedValue");
                if (feature != null) {
                    Object result = ((EObject) value).eGet(feature);
                    if (result instanceof Number) {
                        return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.DOUBLE)
                                .setDoubleValue(((Number) result).doubleValue());
                    }
                }
            } catch (Exception e) {
                // Fall through to string
            }
            return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.STRING)
                    .setStringValue(value.toString());
        } else {
            return Attribute.of(Name.of(name, Namespaces.GENERICS), DataType.STRING)
                    .setStringValue(value.toString());
        }
    }

    private String ifcValueToString(IfcValue value) {
        if (value instanceof IfcLabel lv) return lv.getWrappedValue();
        if (value instanceof IfcText tv) return tv.getWrappedValue();
        if (value instanceof IfcIdentifier idv) return idv.getWrappedValue();
        if (value instanceof IfcBoolean bv) return bv.getWrappedValue() == Tristate.TRUE ? "True" : "False";
        if (value instanceof IfcInteger iv) return String.valueOf(iv.getWrappedValue());
        if (value instanceof IfcReal rv) return String.valueOf(rv.getWrappedValue());
        return value.toString();
    }
}
