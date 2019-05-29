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
package it.istat.is2.dataset.controller;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import it.istat.is2.app.bean.InputFormBean;
import it.istat.is2.app.service.NotificationService;
import it.istat.is2.app.util.FileHandler;
import it.istat.is2.app.util.IS2Const;
import it.istat.is2.dataset.domain.DatasetColonna;
import it.istat.is2.dataset.domain.DatasetFile;
import it.istat.is2.dataset.domain.TipoVariabileSum;
import it.istat.is2.dataset.service.DatasetService;
import it.istat.is2.workflow.domain.SxTipoDato;
import it.istat.is2.workflow.service.TipoDatoService;
import it.istat.is2.worksession.domain.WorkSession;
import it.istat.is2.worksession.service.WorkSessionService;

@Controller
public class DatasetController {

    @Autowired
    private DatasetService datasetService;

    @Autowired
    ServletContext context;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MessageSource messages;

    @Autowired
    private WorkSessionService sessioneLavoroService;
    
    @Autowired
    private TipoDatoService tipoDatoService;

    @RequestMapping("/loadInputFileSessione/{idsessione}")
    public String carica(Model model, @PathVariable("idsessione") Long idsessione) {
        model.addAttribute("idsessione", idsessione);
        return "dataset/load_file";
    }

    @RequestMapping("/viewDataset/{idfile}")
    public String caricafile(Model model, @PathVariable("idfile") Long idfile) {

        notificationService.removeAllMessages();

        DatasetFile dfile = datasetService.findDataSetFile(idfile);

        List<DatasetColonna> colonne = datasetService.findAllNomeColonne(idfile);
        List<TipoVariabileSum> variabiliSum = datasetService.findAllVariabiliSum();

        model.addAttribute("colonne", colonne);
        model.addAttribute("idfile", idfile);
        model.addAttribute("variabili", variabiliSum);
        model.addAttribute("dfile", dfile);

        return "dataset/preview";
    }

    @RequestMapping("/metadatiDataset/{idfile}")
    public String caricaMetadati(Model model, @PathVariable("idfile") Long idfile) {

        DatasetFile dfile = datasetService.findDataSetFile(idfile);

        List<DatasetColonna> colonne = datasetService.findAllNomeColonne(idfile);
        List<TipoVariabileSum> variabiliSum = datasetService.findAllVariabiliSum();

        model.addAttribute("colonne", colonne);
        model.addAttribute("idfile", idfile);
        model.addAttribute("variabili", variabiliSum);
        model.addAttribute("dfile", dfile);

        return "dataset/edit";
    }

    @GetMapping(value = "/sessione/mostradataset/{id}")
    public String mostradataset(HttpSession session, Model model, @PathVariable("id") Long id) {

        WorkSession sessionelv = sessioneLavoroService.getSessione(id).get();
        if (sessionelv.getDatasetFiles() != null) {
            session.setAttribute(IS2Const.SESSION_DATASET, true);
        }
        
        List<DatasetFile> listaDataset = sessionelv.getDatasetFiles();
        List<SxTipoDato> listaTipoDato = tipoDatoService.findListTipoDato();

        session.setAttribute(IS2Const.SESSION_LV, sessionelv);

        model.addAttribute("listaTipoDato", listaTipoDato);
        model.addAttribute("listaDataset", listaDataset);
        return "dataset/list";
    }
    
    
    @RequestMapping(value = "/associaVarSum", method = RequestMethod.POST)
    public String caricaMetadati(Model model, String idfile, String idvar, String filtro, String idsum) {

        DatasetColonna dcol = datasetService.findOneColonna(Long.parseLong(idvar));
        TipoVariabileSum sum = new TipoVariabileSum(Integer.parseInt(idsum));

        dcol.setTipoVariabile(sum);
        dcol.setFiltro(new Short(filtro));
        try {
            datasetService.salvaColonna(dcol);
            notificationService.addInfoMessage("Salvataggio avvenuto con successo!");
        } catch (Exception e) {
            notificationService.addErrorMessage("Errore: ", e.getMessage());

        }

        List<DatasetColonna> colonne = datasetService.findAllNomeColonne(Long.parseLong(idfile));
        List<TipoVariabileSum> variabiliSum = datasetService.findAllVariabiliSum();

        model.addAttribute("colonne", colonne);
        model.addAttribute("idfile", idfile);
        model.addAttribute("variabili", variabiliSum);

        return "dataset/edit";
    }

    @RequestMapping(value = "/loadInputData", method = RequestMethod.POST)
    public String loadInputData(HttpSession session, HttpServletRequest request,
            Model model, @ModelAttribute("inputFormBean") InputFormBean form) throws IOException {

        notificationService.removeAllMessages();

        String labelFile = form.getLabelFile();
        Integer tipoDato = form.getTipoDato();
        String separatore = form.getDelimiter();
        String idsessione = form.getIdsessione();

        File f = FileHandler.convertMultipartFileToFile(form.getFileName());
        String pathTmpFile = f.getAbsolutePath().replace("\\", "/");

        HashMap<Integer, String> valoriHeaderNum = null;
        try {
            valoriHeaderNum = FileHandler.getCampiHeaderNumIndex(pathTmpFile, separatore.toCharArray()[0]);
        } catch (Exception e) {
            notificationService.addErrorMessage(messages.getMessage("file.read.error", null, LocaleContextHolder.getLocale()), e.getMessage());
            return "redirect:/sessione/mostradataset/" + idsessione;
        }

        HashMap<String, ArrayList<String>> campiL = null;
        try {
            campiL = FileHandler.getArrayListFromCsv2(pathTmpFile, form.getNumeroCampi(), separatore.toCharArray()[0], valoriHeaderNum);
        } catch (Exception e) {
            notificationService.addErrorMessage(messages.getMessage("file.read.error", null, LocaleContextHolder.getLocale()), e.getMessage());
        }

        try {
            datasetService.salva(campiL, valoriHeaderNum, labelFile, tipoDato, separatore, form.getDescrizione(), idsessione);
            notificationService.addInfoMessage("Salvataggio avvenuto con successo.");
        } catch (Exception e) {
            notificationService.addErrorMessage("Errore nel salvataggio del file.");
            return "redirect:/sessione/mostradataset/" + idsessione;
        }

        return "redirect:/sessione/mostradataset/" + idsessione;
    }

    @RequestMapping("/createField/{idfile}/{idColonna}/{commandField}/{charOrString}/{upperLower}/{newField}/{columnOrder}")
    public String createField(Model model, @PathVariable("idfile") String idfile, @PathVariable("idColonna") String idColonna,
            @PathVariable("commandField") String commandField, @PathVariable("charOrString") String charOrString,
            @PathVariable("upperLower") String upperLower, @PathVariable("newField") String newField,
            @PathVariable("columnOrder") String columnOrder) {

        DatasetFile dFile = datasetService.createField(idfile, idColonna, commandField, charOrString, upperLower, newField, columnOrder);

        List<DatasetColonna> colonne = datasetService.findAllNomeColonne(Long.parseLong(idfile));
        List<TipoVariabileSum> variabiliSum = datasetService.findAllVariabiliSum();

        model.addAttribute("colonne", colonne);
        model.addAttribute("idfile", idfile);
        model.addAttribute("variabili", variabiliSum);
        model.addAttribute("dfile", dFile);

        return "dataset/preview";
    }

    @RequestMapping(value = "/deleteDataset", method = RequestMethod.POST)
    public String deleteDataset(Model model, @RequestParam("idDataset") Long idDataset) {

        notificationService.removeAllMessages();

        WorkSession sessionelv = sessioneLavoroService.getSessioneByIdFile(idDataset);
        datasetService.deleteDataset(idDataset);
        notificationService.addInfoMessage("Eliminazione avvenuta con successo");

        return "redirect:/sessione/mostradataset/" + sessionelv.getId();
    }
}