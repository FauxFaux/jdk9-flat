/*
 * Copyright 1997-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.crypto.provider;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactorySpi;
import javax.crypto.spec.DESedeKeySpec;
import java.security.InvalidKeyException;
import java.security.spec.KeySpec;
import java.security.spec.InvalidKeySpecException;

/**
 * This class implements the DES-EDE key factory of the Sun provider.
 *
 * @author Jan Luehe
 *
 */

public final class DESedeKeyFactory extends SecretKeyFactorySpi {

    /**
     * Verify the SunJCE provider in the constructor.
     *
     * @exception SecurityException if fails to verify
     * its own integrity
     */
    public DESedeKeyFactory() {
        if (!SunJCE.verifySelfIntegrity(this.getClass())) {
            throw new SecurityException("The SunJCE provider may have been " +
                                        "tampered.");
        }
    }

    /**
     * Generates a <code>SecretKey</code> object from the provided key
     * specification (key material).
     *
     * @param keySpec the specification (key material) of the secret key
     *
     * @return the secret key
     *
     * @exception InvalidKeySpecException if the given key specification
     * is inappropriate for this key factory to produce a public key.
     */
    protected SecretKey engineGenerateSecret(KeySpec keySpec)
        throws InvalidKeySpecException {
        DESedeKey desEdeKey = null;

        try {
            if (keySpec instanceof DESedeKeySpec) {
                DESedeKeySpec desEdeKeySpec = (DESedeKeySpec)keySpec;
                desEdeKey = new DESedeKey(desEdeKeySpec.getKey());

            } else {
                throw new InvalidKeySpecException
                    ("Inappropriate key specification");
            }
        } catch (InvalidKeyException e) {
        }
        return desEdeKey;
    }

    /**
     * Returns a specification (key material) of the given key
     * in the requested format.
     *
     * @param key the key
     *
     * @param keySpec the requested format in which the key material shall be
     * returned
     *
     * @return the underlying key specification (key material) in the
     * requested format
     *
     * @exception InvalidKeySpecException if the requested key specification is
     * inappropriate for the given key, or the given key cannot be processed
     * (e.g., the given key has an unrecognized algorithm or format).
     */
    protected KeySpec engineGetKeySpec(SecretKey key, Class keySpec)
        throws InvalidKeySpecException {

        try {
            if ((key instanceof SecretKey)
                && (key.getAlgorithm().equalsIgnoreCase("DESede"))
                && (key.getFormat().equalsIgnoreCase("RAW"))) {

                // Check if requested key spec is amongst the valid ones
                if (DESedeKeySpec.class.isAssignableFrom(keySpec)) {
                    return new DESedeKeySpec(key.getEncoded());

                } else {
                    throw new InvalidKeySpecException
                        ("Inappropriate key specification");
                }

            } else {
                throw new InvalidKeySpecException
                    ("Inappropriate key format/algorithm");
            }
        } catch (InvalidKeyException e) {
            throw new InvalidKeySpecException("Secret key has wrong size");
        }
    }

    /**
     * Translates a <code>SecretKey</code> object, whose provider may be
     * unknown or potentially untrusted, into a corresponding
     * <code>SecretKey</code> object of this key factory.
     *
     * @param key the key whose provider is unknown or untrusted
     *
     * @return the translated key
     *
     * @exception InvalidKeyException if the given key cannot be processed by
     * this key factory.
     */
    protected SecretKey engineTranslateKey(SecretKey key)
        throws InvalidKeyException {

        try {

            if ((key != null)
                && (key.getAlgorithm().equalsIgnoreCase("DESede"))
                && (key.getFormat().equalsIgnoreCase("RAW"))) {
                // Check if key originates from this factory
                if (key instanceof com.sun.crypto.provider.DESedeKey) {
                    return key;
                }
                // Convert key to spec
                DESedeKeySpec desEdeKeySpec
                    = (DESedeKeySpec)engineGetKeySpec(key,
                                                      DESedeKeySpec.class);
                // Create key from spec, and return it
                return engineGenerateSecret(desEdeKeySpec);

            } else {
                throw new InvalidKeyException
                    ("Inappropriate key format/algorithm");
            }

        } catch (InvalidKeySpecException e) {
            throw new InvalidKeyException("Cannot translate key");
        }
    }
}
