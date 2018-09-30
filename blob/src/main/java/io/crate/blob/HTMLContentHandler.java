/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.blob;

import org.xml.sax.SAXException;

import org.xml.sax.ContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.sax.ContentHandlerDecorator;

public class HTMLContentHandler extends ContentHandlerDecorator {

    private boolean isTitleTagOpen;
    private static final String TITLE_TAG = "TITLE";

    public HTMLContentHandler() {
        super();
    }

    public HTMLContentHandler(ContentHandler handler) {
        super(handler);
    }

    @Override
    public void startDocument() throws SAXException {
        super.startDocument();
        isTitleTagOpen = false;
    }

    @Override
    public void startElement(String uri, String localName, String name, Attributes origAttrs) throws SAXException {
        if ("img".equals(localName)) {
            AttributesImpl attrs;
            if (origAttrs instanceof AttributesImpl) {
                attrs = (AttributesImpl)origAttrs;
            } else {
                attrs = new AttributesImpl(origAttrs);
            }

            for (int i = 0; i < attrs.getLength(); i++) {
                if ("src".equals(attrs.getLocalName(i))) {
                    String src = attrs.getValue(i);
                    if (src.startsWith("embedded:")) {
                        String newSrc = src.replace("embedded:", "images\\\\");
                        attrs.setValue(i, newSrc);
                    }
                }
            }
            attrs.addAttribute(null, "width", "width","width", "100px");
            super.startElement(uri, localName, name, attrs);
        } else {
            super.startElement(uri, localName, name, origAttrs);
        }

        if (TITLE_TAG.equalsIgnoreCase(localName) && XHTMLContentHandler.XHTML.equals(uri)) {
            isTitleTagOpen = true;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        super.endElement(uri, localName, qName);
        if (TITLE_TAG.equalsIgnoreCase(localName) && XHTMLContentHandler.XHTML.equals(uri)) {
            isTitleTagOpen = false;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        if (isTitleTagOpen && length == 0) {
            // Hack to close the title tag
            try {
                super.characters(new char[0], 0, 1);
            } catch (ArrayIndexOutOfBoundsException e) {
                // Expected, just wanted to close the title tag
            }
        } else {
            super.characters(ch, start, length);
        }
    }
}
