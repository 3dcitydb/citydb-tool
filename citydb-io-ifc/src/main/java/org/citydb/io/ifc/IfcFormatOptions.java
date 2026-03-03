package org.citydb.io.ifc;

import org.citydb.config.SerializableConfig;
import org.citydb.io.reader.options.InputFormatOptions;

@SerializableConfig(name = "IFC")
public class IfcFormatOptions implements InputFormatOptions {
    private boolean reorientShells;
    private boolean noProperties;
    private boolean noAppearances;
    private boolean noStoreys;
    private boolean noReferences;
    private boolean noGenericAttributeSets;
    private boolean psetNamesAsPrefixes;
    private boolean listUnmappedDoorsAndWindows;
    private boolean unrelatedDoorsAndWindowsInDummyBce;
    private double xOffset;
    private double yOffset;
    private double zOffset;
    private int targetSrid;

    public boolean isReorientShells() {
        return reorientShells;
    }

    public IfcFormatOptions setReorientShells(boolean reorientShells) {
        this.reorientShells = reorientShells;
        return this;
    }

    public boolean isNoProperties() {
        return noProperties;
    }

    public IfcFormatOptions setNoProperties(boolean noProperties) {
        this.noProperties = noProperties;
        return this;
    }

    public boolean isNoAppearances() {
        return noAppearances;
    }

    public IfcFormatOptions setNoAppearances(boolean noAppearances) {
        this.noAppearances = noAppearances;
        return this;
    }

    public boolean isNoStoreys() {
        return noStoreys;
    }

    public IfcFormatOptions setNoStoreys(boolean noStoreys) {
        this.noStoreys = noStoreys;
        return this;
    }

    public boolean isNoReferences() {
        return noReferences;
    }

    public IfcFormatOptions setNoReferences(boolean noReferences) {
        this.noReferences = noReferences;
        return this;
    }

    public boolean isNoGenericAttributeSets() {
        return noGenericAttributeSets;
    }

    public IfcFormatOptions setNoGenericAttributeSets(boolean noGenericAttributeSets) {
        this.noGenericAttributeSets = noGenericAttributeSets;
        return this;
    }

    public boolean isPsetNamesAsPrefixes() {
        return psetNamesAsPrefixes;
    }

    public IfcFormatOptions setPsetNamesAsPrefixes(boolean psetNamesAsPrefixes) {
        this.psetNamesAsPrefixes = psetNamesAsPrefixes;
        return this;
    }

    public boolean isListUnmappedDoorsAndWindows() {
        return listUnmappedDoorsAndWindows;
    }

    public IfcFormatOptions setListUnmappedDoorsAndWindows(boolean listUnmappedDoorsAndWindows) {
        this.listUnmappedDoorsAndWindows = listUnmappedDoorsAndWindows;
        return this;
    }

    public boolean isUnrelatedDoorsAndWindowsInDummyBce() {
        return unrelatedDoorsAndWindowsInDummyBce;
    }

    public IfcFormatOptions setUnrelatedDoorsAndWindowsInDummyBce(boolean unrelatedDoorsAndWindowsInDummyBce) {
        this.unrelatedDoorsAndWindowsInDummyBce = unrelatedDoorsAndWindowsInDummyBce;
        return this;
    }

    public double getXOffset() {
        return xOffset;
    }

    public IfcFormatOptions setXOffset(double xOffset) {
        this.xOffset = xOffset;
        return this;
    }

    public double getYOffset() {
        return yOffset;
    }

    public IfcFormatOptions setYOffset(double yOffset) {
        this.yOffset = yOffset;
        return this;
    }

    public double getZOffset() {
        return zOffset;
    }

    public IfcFormatOptions setZOffset(double zOffset) {
        this.zOffset = zOffset;
        return this;
    }

    public int getTargetSrid() {
        return targetSrid;
    }

    public IfcFormatOptions setTargetSrid(int targetSrid) {
        this.targetSrid = targetSrid;
        return this;
    }
}
