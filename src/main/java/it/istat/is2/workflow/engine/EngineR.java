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
 * @author Paolo Francescangeli  <pafrance @ istat.it>
 * @author Renzo Iannacone <iannacone @ istat.it>
 * @author Stefano Macone <macone @ istat.it>
 * @version 1.0
 */
package it.istat.is2.workflow.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.RList;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.istat.is2.app.service.LogService;
import it.istat.is2.app.util.IS2Const;
import it.istat.is2.app.util.Utility;
import it.istat.is2.workflow.dao.AppRoleDao;
import it.istat.is2.workflow.dao.StepRuntimeDao;
import it.istat.is2.workflow.domain.AppRole;
import it.istat.is2.workflow.domain.DataProcessing;
import it.istat.is2.workflow.domain.DataTypeCls;
import it.istat.is2.workflow.domain.StepInstance;
import it.istat.is2.workflow.domain.StepInstanceSignature;
import it.istat.is2.workflow.domain.StepRuntime;
import it.istat.is2.workflow.domain.TypeIO;
import it.istat.is2.workflow.domain.Workset;


@Service
public class EngineR implements EngineService {

    @Autowired
    AppRoleDao ruoloDao;
    @Autowired
    StepRuntimeDao stepRuntimeDao;
    @Autowired
    LogService logService;

    @Value("${serverR.host}")
    private String serverRHost;
    @Value("${serverR.port}")
    private Integer serverRPort;

    @Value("${path.script.R}")
    private String pathR;

    private String fileScriptR;

    private RConnection connection;

    private String command;

    private DataProcessing dataProcessing;
    private StepInstance stepInstance;
    private LinkedHashMap<String, ArrayList<StepRuntime>> dataMap;
    private Map<String, AppRole> rolesMap;
    private LinkedHashMap<String, ArrayList<String>> parametersMap;
    private LinkedHashMap<String, ArrayList<String>> worksetVariables;
    private LinkedHashMap<String, ArrayList<String>> ruleset;

    private LinkedHashMap<String, ArrayList<String>> variablesRolesMap;
    private LinkedHashMap<String, String> rolesVariablesMap;

    private LinkedHashMap<String, ArrayList<String>> worksetOut;
    private LinkedHashMap<String, String> parameterOut;
    private LinkedHashMap<String, ArrayList<String>> rolesOut;
    private LinkedHashMap<String, String> rolesGroupOut;

    public EngineR(String serverRHost, int serverRPort, String pathR, String fileScriptR) {
        super();
        this.serverRHost = serverRHost;
        this.serverRPort = serverRPort;
        this.pathR = pathR;
        this.fileScriptR = fileScriptR;
        connection = null;
        command = "";
    }

    public EngineR() {
        super();
        connection = null;
        command = "";
    }

    @Override
    public void init(DataProcessing dataProcessing, StepInstance stepInstance) throws Exception {
        // Create a connection to Rserve instance running on default port 6311
        this.dataProcessing = dataProcessing;
        this.stepInstance = stepInstance;
        this.fileScriptR = stepInstance.getAppService().getSource();

        prepareEnv();
        createConnection(serverRHost, serverRPort);
        bindInputColumns(worksetVariables, WORKSET_IN);
        bindInputColumnsParams(parametersMap, PARAMETERS_IN);
        bindInputColumns(ruleset, RULESET);
        setRoles(variablesRolesMap);

    }

    private void createConnection(String server, int port) throws RserveException {
        // Create a connection to Rserve instance running on default port 6311
        if (port == 0) {
            port = 6311;
        }

        if (server == null) {
            connection = new RConnection();
        } else {
            connection = new RConnection();
        }
        connection.eval("setwd('" + pathR + "')");
        connection.eval("source('" + fileScriptR + "')");
        Logger.getRootLogger().debug("Script Loaded");
    }

    public void closeConnection() {
        if (connection != null) {
            connection.close();
        }
    }

    public void bindInputColumns(LinkedHashMap<String, ArrayList<String>> workset, String varR)
            throws REngineException {

        if (!workset.isEmpty()) {
            List<String> keys = new ArrayList<String>(workset.keySet());
            String listaCampi = "";
            int size = keys.size();
            String key = "";

            for (int i = 0; i < size; i++) {
                key = keys.get(i);

                String[] arrX = workset.get(key).toArray(new String[workset.get(key).size()]);
                listaCampi += key + ",";
                connection.assign(key, arrX); // Create a string vector
                try {
                    if (isNumeric(arrX)) {
                        connection.eval(key + " <- as.numeric(" + key + ")");
                    }
                } catch (Exception e) {
                    Logger.getRootLogger().error(e.getMessage());
                }

            }
            listaCampi = listaCampi.substring(0, listaCampi.length() - 1);
            
            connection.eval(varR + " <- data.frame(" + listaCampi + ")"); // Create a data frame
        }
    }

    public void bindInputColumnsParams(LinkedHashMap<String, ArrayList<String>> workset, String varR)
            throws REngineException {

        if (!workset.isEmpty()) {
            List<String> keys = new ArrayList<>(workset.keySet());
            String listaCampi = "";
            int size = keys.size();
            String key;

            for (int i = 0; i < size; i++) {
                key = keys.get(i);
                String[] arrX = workset.get(key).toArray(new String[workset.get(key).size()]);
                listaCampi += key + ",";
                connection.assign(key, arrX);
            }
            
            listaCampi = listaCampi.substring(0, listaCampi.length() - 1);
            
            connection.eval(varR + " <- list(" + listaCampi + ")");
        }
    }

    private boolean isNumeric(String[] arrX) {
        for (String elem : arrX) {
            if (elem.isEmpty() || DEFAULT_NA.equalsIgnoreCase(elem)) {
                continue;
            }
            try {
                Double.parseDouble(elem);
            } catch (NumberFormatException | NullPointerException nfe) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void doAction() throws RserveException {

        String fname = stepInstance.getMethod();
        // mlest <- ml.est (workset, y=Y,";
        // Aggiunto il workset e params nella lista degli argomenti della funzione (by
        // paolinux)
        command = RESULTSET + "  <- " + fname + "( " + WORKSET_IN + "," + ROLES_IN + ",";
        if (!parametersMap.isEmpty()) {
            command += PARAMETERS_IN + ",";
        }
        if (!ruleset.isEmpty()) {
            command += RULESET + ",";
        }
        
        command = command.substring(0, command.length() - 1);
        command += ")";
        Logger.getRootLogger().debug("Eseguo " + command);
        
        connection.eval(command);
    }

    // Assegna il ruolo selemix alle variabili del workset
    public void setRoles(LinkedHashMap<String, ArrayList<String>> ruoliVariabileNome) throws RserveException {
        String rolesList = "";
        Logger.getRootLogger().debug("Eseguo SetRuoli>");
        for (Map.Entry<String, ArrayList<String>> entry : ruoliVariabileNome.entrySet()) {
            String roleCode = entry.getKey();
            ArrayList<String> nomeVariabiliList = entry.getValue();
            rolesList += "'" + roleCode + "' = " + Utility.combineList2String4R(nomeVariabiliList) + ",";
        }
        rolesList = rolesList.substring(0, rolesList.length() - 1);
        
        connection.eval(ROLES_IN + " <-list(" + rolesList + ")");
    }

    public void getGenericOutput(LinkedHashMap<String, ArrayList<String>> genericHashMap, String varR,
            String tipoOutput) throws RserveException, REXPMismatchException {
        Logger.getRootLogger().debug("Eseguo Get " + tipoOutput);
        try {
            RList lista = connection.eval(varR + "$" + tipoOutput).asList();
            if (lista != null && !lista.isEmpty()) {
                getRecursiveOutput(genericHashMap, lista);
                Logger.getRootLogger()
                        .debug("Impostati campi di " + tipoOutput + "= " + genericHashMap.values().toString());
            }
        } catch (Exception e) {
            Logger.getRootLogger().info("No such list:" + tipoOutput);
            Logger.getRootLogger().debug(connection.eval("print(str(" + varR + "$" + tipoOutput + "))").toString());
        }
    }

    public void getGenericParamterOutput(LinkedHashMap<String, String> genericHashMap, String varR, String tipoOutput)
            throws RserveException, REXPMismatchException {
        Logger.getRootLogger().debug("Eseguo Get " + tipoOutput);
        try {
            RList lista = connection.eval(varR + "$" + tipoOutput).asList();
            if (lista != null && !lista.isEmpty()) {

                for (int i = 0; i < lista.size(); i++) {
                    String name = lista.names.get(i).toString();
                    String value = lista.at(i).asString();
                    genericHashMap.put(name, value);
                }

                Logger.getRootLogger()
                        .debug("Impostati campi di " + tipoOutput + "= " + genericHashMap.values().toString());
            }
        } catch (Exception e) {
            Logger.getRootLogger().info("No such list:" + tipoOutput);
            Logger.getRootLogger().debug(connection.eval("print(str(" + varR + "$" + tipoOutput + "))").toString());
        }
    }

    public void getRolesGroup(LinkedHashMap<String, String> genericHashMap, String varR, String tipoOutput)
            throws RserveException, REXPMismatchException {
        Logger.getRootLogger().debug("Eseguo Get " + tipoOutput);
        try {
            RList lista = connection.eval(varR + "$" + tipoOutput).asList();
            for (int j = 0; j < lista.size(); j++) {
                String ts[] = lista.at(j).asStrings();
                String name = lista.names.get(j).toString();
                for (int i = 0; i < ts.length; i++) {
                    genericHashMap.put(ts[i], name);
                }
            }
        } catch (Exception e) {
            Logger.getRootLogger().info("No such list:" + tipoOutput);
            Logger.getRootLogger().debug(connection.eval("print(str(" + varR + "$" + tipoOutput + "))").toString());
        }
    }

    public void writeLogScriptR() throws RserveException, REXPMismatchException {
        Logger.getRootLogger().debug("Eseguo getLogScriptR ");
        String rlog[] = connection.eval(RESULTSET + "$log").asStrings();
        for (int i = 0; i < rlog.length; i++) {
            logService.save(rlog[i], IS2Const.OUTPUT_R);
        }
        logService.save("Script completed!");
    }

    public void getRecursiveOutput(LinkedHashMap<String, ArrayList<String>> genericHashMap, RList lista)
            throws RserveException, REXPMismatchException {
        String name;
        if (lista != null) {
            Logger.getRootLogger().info("Campi:> " + lista.names.toString() + " Size(" + lista.size() + ")");
            for (int i = 0; i < lista.size(); i++) {
                if (lista.at(i).isList()) {
                    getRecursiveOutput(genericHashMap, lista.at(i).asList());
                } else {
                    String ts[] = lista.at(i).asStrings();
                    name = lista.names.get(i).toString();
                    genericHashMap.put(name, new ArrayList<String>(Arrays.asList(ts)));
                    Logger.getRootLogger().info(name + " (" + ts.length + "/" + genericHashMap.get(name).size() + "): "
                            + genericHashMap.get(name).toString());
                }
            }
        }
    }

    public void bindInputParams(HashMap<String, ArrayList<String>> parametriMap) throws RserveException {
        for (Map.Entry<String, ArrayList<String>> entry : parametriMap.entrySet()) {
            String nomep = entry.getKey();
            ArrayList<String> valore = entry.getValue();
            // X <- c('X1','X2');
            String comando = nomep + " <- " + Utility.combineList2String4R(valore);
            Logger.getRootLogger().info("Comando: " + comando);
            connection.eval(comando);
        }
    }

    public void prepareEnv() {
        // Get all roles
        rolesMap = ruoloDao.findByServiceAsCodMap(stepInstance.getAppService().getBusinessService());

        // Recupero dei ruoli di INPUT e OUTUPT e dalle istanze
        // {S=[S], X=[X], Y=[Y], Z=[Z]}
        HashMap<String, ArrayList<String>> ruoliInputStep = new HashMap<>();
        // {P=[P], M=[M], O=[O]}
        rolesOut = new LinkedHashMap();
        rolesGroupOut = new LinkedHashMap<>();

        for (Iterator<?> iterator = stepInstance.getStepInstanceSignatures().iterator(); iterator.hasNext();) {
            StepInstanceSignature stepInstanceSignature = (StepInstanceSignature) iterator.next();
            if (stepInstanceSignature.getTypeIO().getId().intValue() == IS2Const.TYPE_IO_INPUT) {
                ArrayList<String> listv = ruoliInputStep.get(stepInstanceSignature.getAppRole().getCode());
                if (listv == null) {
                    listv = new ArrayList<>();
                }
                listv.add(stepInstanceSignature.getAppRole().getCode());
                ruoliInputStep.put(stepInstanceSignature.getAppRole().getCode(), listv);
            } else if (stepInstanceSignature.getTypeIO().getId().intValue() == IS2Const.TYPE_IO_OUTPUT) {
                ArrayList<String> listv = rolesOut.get(stepInstanceSignature.getAppRole().getCode());
                if (listv == null) {
                    listv = new ArrayList<>();
                }
                listv.add(stepInstanceSignature.getAppRole().getCode());
                rolesOut.put(stepInstanceSignature.getAppRole().getCode(), listv);
            }
        }

        // Get input workset
        List<StepRuntime> dataList = stepRuntimeDao.findByDataProcessing(dataProcessing);
        // mappa delle colonne workset <nome campo, oggetto stepv>
        dataMap = Utility.getMapNameWorkSetStep(dataList);
        // mappa delle colonne workset <nome campo, oggetto stepv>
        Map<String, ArrayList<StepRuntime>> dataRuoliStepVarMap = Utility.getMapCodiceRuoloStepVariabili(dataList);
        // List<AppRole> ruoliAll = ruoloDao.findAll();
        // Utility.getMapRuoliByCod(ruoliAll)

        // mappa delle colonne workset <nome,lista valori>
        worksetVariables = Utility.getMapWorkSetValuesInRoles(dataMap, new DataTypeCls(IS2Const.DATA_TYPE_VARIABLE),
                ruoliInputStep.keySet());

        // Parameters
        parametersMap = Utility.getMapWorkSetValues(dataMap, new DataTypeCls(IS2Const.DATA_TYPE_PARAMETER));
        ruleset = Utility.getMapWorkSetValues(dataMap, new DataTypeCls(IS2Const.DATA_TYPE_RULESET));
        worksetOut = new LinkedHashMap<>();

        // associo il codice ruolo alla variabile
        // codiceRuolo, lista nome variabili {X=[X1], Y=[Y1]}
        variablesRolesMap = new LinkedHashMap<>();
        rolesVariablesMap = new LinkedHashMap<>();
        parameterOut = new LinkedHashMap<>();

        for (Map.Entry<String, ArrayList<StepRuntime>> entry : dataRuoliStepVarMap.entrySet()) {
            String codR = entry.getKey();
            ArrayList<StepRuntime> listSVariable = entry.getValue();
            for (Iterator<StepRuntime> iterator = listSVariable.iterator(); iterator.hasNext();) {
                StepRuntime stepRuntime = (StepRuntime) iterator.next();

                rolesVariablesMap.put(stepRuntime.getWorkset().getName(), codR);

                ArrayList<String> listv = variablesRolesMap.get(codR);
                if (listv == null) {
                    listv = new ArrayList<>();
                }
                listv.add(stepRuntime.getWorkset().getName());
                variablesRolesMap.put(codR, listv);
            }
        }

    }

    @Override
    public void processOutput() throws Exception {
        getGenericOutput(worksetOut, RESULTSET, WORKSET_OUT);
        getGenericOutput(rolesOut, RESULTSET, ROLES_OUT);
        getGenericParamterOutput(parameterOut, RESULTSET, PARAMETERS_OUT);
        getGenericParamterOutput(parameterOut, RESULTSET, REPORT_OUT);
        //getGenericOutput(worksetOut, RESULTSET, REPORT_OUT);
        getRolesGroup(rolesGroupOut, RESULTSET, ROLES_GROUP_OUT);
        writeLogScriptR();
        saveOutputDB();

    }

    private void saveOutputDB() {
        HashMap<String, String> ruoliOutputStepInversa = new HashMap<>();
        for (Map.Entry<String, ArrayList<String>> entry : rolesOut.entrySet()) {
            String nomeR = entry.getKey();
            ArrayList<String> value = entry.getValue();
            value.forEach((nomevar) -> ruoliOutputStepInversa.put(nomevar, nomeR));
        }

        // salva output su DB
        for (Map.Entry<String, ArrayList<String>> entry : worksetOut.entrySet()) {
            String nomeW = entry.getKey();
            ArrayList<String> value = entry.getValue();
            StepRuntime stepRuntime;
            String ruolo = ruoliOutputStepInversa.get(nomeW);
            String ruoloGruppo = rolesGroupOut.get(ruolo);
            if (ruolo == null) {
                ruolo = ROLE_DEFAULT;
            }
            if (ruoloGruppo == null) {
                ruoloGruppo = ROLE_DEFAULT;
            }
            AppRole sxRuolo = rolesMap.get(ruolo);
            AppRole sxRuoloGruppo = rolesMap.get(ruoloGruppo);

            stepRuntime = Utility.retrieveStepRuntime(dataMap, nomeW, sxRuolo);

            if (stepRuntime != null) { // update
                stepRuntime.getWorkset().setContents(value);
                stepRuntime.setTypeIO(new TypeIO(IS2Const.TYPE_IO_OUTPUT));
            } else {
                stepRuntime = new StepRuntime();
                stepRuntime.setDataProcessing(dataProcessing);
                stepRuntime.setTypeIO(new TypeIO(IS2Const.TYPE_IO_OUTPUT));
                stepRuntime.setDataType(new DataTypeCls(IS2Const.DATA_TYPE_VARIABLE));
                stepRuntime.setAppRole(sxRuolo);
                stepRuntime.setRoleGroup(sxRuoloGruppo);
                stepRuntime.setOrderCode(sxRuolo.getOrder());
                Workset workset = new Workset();
                workset.setName(nomeW.replaceAll("\\.", "_"));
                workset.setDataType(new DataTypeCls(IS2Const.DATA_TYPE_VARIABLE));
                ArrayList<StepRuntime> l = new ArrayList<>();
                l.add(stepRuntime);
                workset.setStepRuntimes(l);
                workset.setContents(value);
                workset.setContentSize(workset.getContents().size());
                stepRuntime.setWorkset(workset);
            }

            stepRuntimeDao.save(stepRuntime);
        }

        // save output Parameter DB
        for (Map.Entry<String, String> entry : parameterOut.entrySet()) {
            String nomeW = entry.getKey();
            String value = entry.getValue();
            StepRuntime stepRuntime;
            String ruolo = ruoliOutputStepInversa.get(nomeW);
            String ruoloGruppo = rolesGroupOut.get(ruolo);
            if (ruolo == null) {
                ruolo = ROLE_DEFAULT;
            }
            if (ruoloGruppo == null) {
                ruoloGruppo = ROLE_DEFAULT;
            }
            AppRole sxRuolo = rolesMap.get(ruolo);
            AppRole sxRuoloGruppo = rolesMap.get(ruoloGruppo);

            stepRuntime = Utility.retrieveStepRuntime(dataMap, nomeW, sxRuolo);

            if (stepRuntime != null) { // update

                stepRuntime.getWorkset().setParamValue(value);
                stepRuntime.setTypeIO(new TypeIO(IS2Const.TYPE_IO_OUTPUT));
            } else {
                stepRuntime = new StepRuntime();
                stepRuntime.setDataProcessing(dataProcessing);
                stepRuntime.setTypeIO(new TypeIO(IS2Const.TYPE_IO_OUTPUT));
                stepRuntime.setDataType(new DataTypeCls(IS2Const.DATA_TYPE_PARAMETER));
                stepRuntime.setAppRole(sxRuolo);
                stepRuntime.setRoleGroup(sxRuoloGruppo);
                stepRuntime.setOrderCode(sxRuolo.getOrder());
                Workset workset = new Workset();
                workset.setName(nomeW.replaceAll("\\.", "_"));

                ArrayList<StepRuntime> l = new ArrayList<>();
                l.add(stepRuntime);
                workset.setStepRuntimes(l);
                workset.setParamValue(value);
                workset.setContentSize(1);
                workset.setDataType(new DataTypeCls(IS2Const.DATA_TYPE_PARAMETER));
                stepRuntime.setWorkset(workset);
            }

            stepRuntimeDao.save(stepRuntime);
        }

    }

    @Override
    public void destroy() {
        closeConnection();

    }

    @Override
    public void init() throws Exception {
        // Do nothing
    }
}
