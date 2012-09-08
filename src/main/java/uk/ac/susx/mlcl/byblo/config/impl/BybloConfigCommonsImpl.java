/*
 * Copyright (c) 2010-2012, University of Sussex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the University of Sussex nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package uk.ac.susx.mlcl.byblo.config.impl;

import java.nio.charset.Charset;
import java.util.Locale;
import org.apache.commons.configuration.Configuration;
import uk.ac.susx.mlcl.byblo.config.BybloConfig;
import uk.ac.susx.mlcl.byblo.config.FileFormatConfig;
import uk.ac.susx.mlcl.byblo.config.MeasureConfig;
import uk.ac.susx.mlcl.byblo.config.WeightingConfig;

/**
 *
 * @author hamish
 */
public class BybloConfigCommonsImpl
        extends AbstractCommonsImpl
        implements BybloConfig {

    public BybloConfigCommonsImpl(Configuration config) {
        super(config);
    }

    @Override
    public MeasureConfig getMeasure() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public WeightingConfig[] getWeightings() {
        
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Charset getCharset() {
        return Charset.forName(getString("charset"));
    }

    @Override
    public Locale getLocale() {
        return decodeLocaleString(getString("locale"));
    }


    @Override
    public MeasureConfig[] getMeasures() {
        System.out.println(subset("measures").getKeys());
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public FileFormatConfig[] getFileFormats() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
