/**
 *  Copyright Terracotta, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.sf.ehcache.config.generator.xsom;

import net.sf.ehcache.config.MemoryUnit;

import java.util.Iterator;
import java.util.Random;

import com.sun.xml.xsom.XSAttributeDecl;
import com.sun.xml.xsom.XSFacet;
import com.sun.xml.xsom.XSRestrictionSimpleType;
import com.sun.xml.xsom.XSSimpleType;

public abstract class XSDAttributeValueType {

    protected static final Random RANDOM = new Random(System.currentTimeMillis());
    protected String pattern;
    public enum XsdType {
        BOOLEAN, INTEGER, POSITIVE_INTEGER, NON_NEGATIVE_INTEGER, STRING, ANY_SIMPLE_TYPE, ENUMERATION;
    }
    protected String maxValue;
    protected String minValue;
    protected String length;
    protected String maxLength;
    protected String minLength;

    protected String totalDigits;

    private final XsdType type;

    public XSDAttributeValueType(XsdType type) {
        this.type = type;
    }

    public abstract String getRandomAllowedValue();

    protected void fillUpRestrictions(XSAttributeDecl attributeDecl) {
        XSSimpleType localType = attributeDecl.getType();
        XSRestrictionSimpleType restriction = localType.asRestriction();
        if (restriction != null) {
            Iterator<? extends XSFacet> i = restriction.getDeclaredFacets().iterator();
            while (i.hasNext()) {
                XSFacet facet = i.next();
                if (facet.getName().equals(XSFacet.FACET_MAXINCLUSIVE)) {
                    maxValue = facet.getValue().value;
                }
                if (facet.getName().equals(XSFacet.FACET_MININCLUSIVE)) {
                    minValue = facet.getValue().value;
                }
                if (facet.getName().equals(XSFacet.FACET_MAXEXCLUSIVE)) {
                    maxValue = String.valueOf(Integer.parseInt(facet.getValue().value) - 1);
                }
                if (facet.getName().equals(XSFacet.FACET_MINEXCLUSIVE)) {
                    minValue = String.valueOf(Integer.parseInt(facet.getValue().value) + 1);
                }
                if (facet.getName().equals(XSFacet.FACET_LENGTH)) {
                    length = facet.getValue().value;
                }
                if (facet.getName().equals(XSFacet.FACET_MAXLENGTH)) {
                    maxLength = facet.getValue().value;
                }
                if (facet.getName().equals(XSFacet.FACET_MINLENGTH)) {
                    minLength = facet.getValue().value;
                }
                if (facet.getName().equals(XSFacet.FACET_PATTERN)) {
                    pattern = facet.getValue().value;
                }
                if (facet.getName().equals(XSFacet.FACET_TOTALDIGITS)) {
                    totalDigits = facet.getValue().value;
                }
            }
        }
    }

    public static class XSDAttributeValueBooleanType extends XSDAttributeValueType {

        public XSDAttributeValueBooleanType() {
            super(XsdType.BOOLEAN);
        }

        @Override
        public String getRandomAllowedValue() {
            return RANDOM.nextInt() % 2 == 0 ? getTrue() : getFalse();
        }

        public String getTrue() {
            return String.valueOf(true);
        }

        public String getFalse() {
            return String.valueOf(false);
        }

    }

    public static class XSDAttributeValueIntegerType extends XSDAttributeValueType {

        public XSDAttributeValueIntegerType() {
            super(XsdType.INTEGER);
        }

        @Override
        public String getRandomAllowedValue() {
            return String.valueOf(RANDOM.nextInt());
        }

    }

    public static class XSDAttributeValuePositiveIntegerType extends XSDAttributeValueType {

        public XSDAttributeValuePositiveIntegerType() {
            super(XsdType.POSITIVE_INTEGER);
        }

        @Override
        public String getRandomAllowedValue() {
            return String.valueOf(Math.abs(RANDOM.nextInt() + 1));
        }

    }

    public static class XSDAttributeValueNonNegativeIntegerType extends XSDAttributeValueType {

        public XSDAttributeValueNonNegativeIntegerType() {
            super(XsdType.NON_NEGATIVE_INTEGER);
        }

        @Override
        public String getRandomAllowedValue() {
            return String.valueOf(Math.abs(RANDOM.nextInt()));
        }

    }

    public static class XSDAttributeValueMemoryUnitType extends XSDAttributeValueType {

        public static final char[] unitChars;

        static {
            MemoryUnit[] values = MemoryUnit.values();
            char [] chars = new char[values.length * 2];
            int i = 0;
            for (MemoryUnit memoryUnit : values) {
                chars[i++] = Character.toLowerCase(memoryUnit.getUnit());
                chars[i++] = Character.toUpperCase(memoryUnit.getUnit());
            }
            unitChars = chars;
        }

        public XSDAttributeValueMemoryUnitType() {
            super(XsdType.NON_NEGATIVE_INTEGER);
        }

        @Override
        public String getRandomAllowedValue() {
            int index = RANDOM.nextInt(unitChars.length + 1);
            if(index < unitChars.length) {
                return String.valueOf(Math.abs(RANDOM.nextInt())) + unitChars[index];
            }
            return String.valueOf(Math.abs(RANDOM.nextInt()));
        }

    }

    public static class XSDAttributeValueMemoryUnitOrPercentageType extends XSDAttributeValueType {

        public static final char[] unitChars;

        static {
            MemoryUnit[] values = MemoryUnit.values();
            char [] chars = new char[values.length * 2 + 1];
            int i = 0;
            for (MemoryUnit memoryUnit : values) {
                chars[i++] = Character.toLowerCase(memoryUnit.getUnit());
                chars[i++] = Character.toUpperCase(memoryUnit.getUnit());
            }
            chars[i] = '%';
            unitChars = chars;
        }

        public XSDAttributeValueMemoryUnitOrPercentageType() {
            super(XsdType.NON_NEGATIVE_INTEGER);
        }

        @Override
        public String getRandomAllowedValue() {
            int index = RANDOM.nextInt(unitChars.length + 1);
            if(index < unitChars.length) {
                switch (unitChars[index]) {
                    case '%' :
                        return String.valueOf(Math.abs(RANDOM.nextInt(100) + 1)) + unitChars[index];
                    default:
                        return String.valueOf(Math.abs(RANDOM.nextInt())) + unitChars[index];
                }
            }
            return String.valueOf(Math.abs(RANDOM.nextInt()));
        }

    }

    public static class XSDAttributeValueStringType extends XSDAttributeValueType {

        private static final String[] RANDOM_VALUES = {"random_string_one", "random_string_two", "random_string_three", "random_string_four",
                "random_string_five", };

        public XSDAttributeValueStringType() {
            super(XsdType.STRING);
        }

        @Override
        public String getRandomAllowedValue() {
            return RANDOM_VALUES[RANDOM.nextInt(RANDOM_VALUES.length)];
        }

    }

    public static class XSDAttributeValueAnySimpleType extends XSDAttributeValueType {

        private static final String[] RANDOM_VALUES = {"any_simple_type_random_one", "any_simple_type_random_two",
                "any_simple_type_random_three", "any_simple_type_random_four", "any_simple_type_random_five", };

        public XSDAttributeValueAnySimpleType() {
            super(XsdType.ANY_SIMPLE_TYPE);
        }

        @Override
        public String getRandomAllowedValue() {
            return RANDOM_VALUES[RANDOM.nextInt(RANDOM_VALUES.length)];
        }

    }

    public static class XSDAttributeValueEnumerationType extends XSDAttributeValueType {

        private final String[] enumeration;

        public XSDAttributeValueEnumerationType(String[] enumeration) {
            super(XsdType.ENUMERATION);
            this.enumeration = enumeration;
        }

        @Override
        public String getRandomAllowedValue() {
            return enumeration[RANDOM.nextInt(enumeration.length)];
        }

    }

}
