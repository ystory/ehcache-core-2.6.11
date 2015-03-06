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

package net.sf.ehcache.config.generator;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.sf.ehcache.config.generator.model.NodeAttribute;
import net.sf.ehcache.config.generator.model.NodeElement;
import net.sf.ehcache.config.generator.model.XMLGeneratorVisitor;

public class AttributeIgnoringXMLGenerator extends XMLGeneratorVisitor {

    private final Set<String> ignoredAttributes;

    public AttributeIgnoringXMLGenerator(PrintWriter out, String... ignoredAttributes) {
        super(out);
        this.ignoredAttributes = new HashSet<String>(Arrays.asList(ignoredAttributes));
    }

    @Override
    protected void visitAttribute(NodeElement element, NodeAttribute attribute) {
        if (ignoredAttributes.contains(attribute.getName())) {
            return;
        }
        super.visitAttribute(element, attribute);
    }

}
