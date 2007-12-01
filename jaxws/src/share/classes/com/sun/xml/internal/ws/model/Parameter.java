/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.xml.internal.ws.model;

import com.sun.xml.internal.bind.api.TypeReference;

import javax.xml.namespace.QName;
import javax.xml.ws.Holder;

/**
 * runtime Parameter that abstracts the annotated java parameter
 *
 * @author Vivek Pandey
 */

public class Parameter {
    /**
     *
     */
    public Parameter(TypeReference type, Mode mode, int index) {
        this.typeReference = type;
        this.name = type.tagName;
        this.mode = mode;
        this.index = index;
    }

    /**
     * @return Returns the name.
     */
    public QName getName() {
        return name;
    }

    /**
     * @return Returns the TypeReference associated with this Parameter
     */
    public TypeReference getTypeReference() {
        return typeReference;
    }

    /**
     * Sometimes we need to overwrite the typeReferenc, such as during patching for rpclit
     * @see RuntimeModel#applyParameterBinding(com.sun.xml.internal.ws.wsdl.parser.Binding)
     */

    void setTypeReference(TypeReference type){
        typeReference = type;
        name = type.tagName;
    }

    /**
     * @return Returns the mode.
     */
    public Mode getMode() {
        return mode;
    }

    /**
     * @return Returns the index.
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return WrapperStyle == true
     */
    public boolean isWrapperStyle() {
        return false;
    }

    /**
     * @return the Binding for this Parameter
     */
    public ParameterBinding getBinding() {
        if(binding == null)
            return ParameterBinding.BODY;
        return binding;
    }

    /**
     * @param binding
     */
    public void setBinding(ParameterBinding binding) {
        this.binding = binding;
    }

    public void setInBinding(ParameterBinding binding){
        this.binding = binding;
    }

    public void setOutBinding(ParameterBinding binding){
        this.outBinding = binding;
    }

    public ParameterBinding getInBinding(){
        return binding;
    }

    public ParameterBinding getOutBinding(){
        if(outBinding == null)
            return binding;
        return outBinding;
    }

    public boolean isIN() {
        return mode==Mode.IN;
    }

    public boolean isOUT() {
        return mode==Mode.OUT;
    }

    public boolean isINOUT() {
        return mode==Mode.INOUT;
    }

    public boolean isResponse() {
        return index == -1;
    }

    /**
     * Creates a holder if applicable else gives the object as it is. To be
     * called on the inbound message.
     *
     * @param value
     * @return the non-holder value if its Response or IN otherwise creates a
     *         holder with the passed value and returns it back.
     *
     */
    public Object createHolderValue(Object value) {
        if (isResponse() || isIN()) {
            return value;
        }
        return new Holder(value);
    }

    /**
     * Gets the holder value if applicable. To be called for inbound client side
     * message.
     *
     * @param obj
     * @return the holder value if applicable.
     */
    public Object getHolderValue(Object obj) {
        if (obj != null && obj instanceof Holder)
            return ((Holder) obj).value;
        return obj;
    }

    public static void setHolderValue(Object obj, Object value) {
        if (obj instanceof Holder)
            ((Holder) obj).value = value;
        else
            // TODO: this can't be correct
            obj = value;
    }

    public String getPartName() {
        if(partName == null)
            return name.getLocalPart();
        return partName;
    }

    public void setPartName(String partName) {
        this.partName = partName;
    }

    protected ParameterBinding binding;
    protected ParameterBinding outBinding;
    protected int index;
    protected Mode mode;
    protected TypeReference typeReference;
    protected QName name;
    protected String partName;
}
