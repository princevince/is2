/**
 * Copyright 2019 ISTAT
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence. You may
 * obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl5
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * Licence for the specific language governing permissions and limitations under
 * the Licence.
 *
 * @author Francesco Amato <framato @ istat.it>
 * @author Mauro Bruno <mbruno @ istat.it>
 * @version 0.1.1
 */
package it.istat.is2.workflow.engine;

import it.istat.is2.app.util.IS2Const;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EngineFactory {

    @Autowired
    private EngineR engineR;
    
    @Autowired
    private EngineJava engineJava;

    @Autowired
    private EngineSQL engineSQL;
 
    public EngineService getEngine(String engineType) {
        if (engineType == null) {
            return null;
        }
        if (engineType.equalsIgnoreCase(IS2Const.ENGINE_R)) {
            return engineR;

        } else if (engineType.equalsIgnoreCase(IS2Const.ENGINE_JAVA)) {
            return engineJava;

        } else if (engineType.equalsIgnoreCase(IS2Const.ENGINE_SQL)) {
            return engineSQL;
            
        }

        return null;
    }
}
