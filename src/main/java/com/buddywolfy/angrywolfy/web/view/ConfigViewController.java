package com.buddywolfy.angrywolfy.web.view;

import com.buddywolfy.angrywolfy.entity.Config;
import com.buddywolfy.angrywolfy.service.ConfigService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ConfigViewController {

    private final ConfigService configService;

    public ConfigViewController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/projects/{projectId}/configs/new")
    public String newForm(@PathVariable Long projectId, Model model) {
        model.addAttribute("configForm", new ConfigForm());
        model.addAttribute("projectId", projectId);
        return "configs/form";
    }

    @PostMapping("/projects/{projectId}/configs")
    public String create(@PathVariable Long projectId,
                          @Valid @ModelAttribute("configForm") ConfigForm form,
                          BindingResult bindingResult,
                          Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("projectId", projectId);
            return "configs/form";
        }
        configService.create(projectId, form.getName(), form.getType(), form.getBaseUrl(),
                toHeaderMap(form.getHeaders()));
        return "redirect:/projects/" + projectId;
    }

    @GetMapping("/configs/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Config config = configService.getById(id);
        model.addAttribute("configForm", ConfigForm.fromEntity(config));
        model.addAttribute("projectId", config.getProject().getId());
        model.addAttribute("configId", config.getId());
        return "configs/form";
    }

    @PostMapping("/configs/{id}")
    public String update(@PathVariable Long id,
                          @Valid @ModelAttribute("configForm") ConfigForm form,
                          BindingResult bindingResult,
                          Model model) {
        Long projectId = configService.getById(id).getProject().getId();
        if (bindingResult.hasErrors()) {
            model.addAttribute("projectId", projectId);
            model.addAttribute("configId", id);
            return "configs/form";
        }
        configService.update(id, form.getName(), form.getType(), form.getBaseUrl(),
                toHeaderMap(form.getHeaders()));
        return "redirect:/projects/" + projectId;
    }

    @PostMapping("/configs/{id}/delete")
    public String delete(@PathVariable Long id) {
        Long projectId = configService.getById(id).getProject().getId();
        configService.delete(id);
        return "redirect:/projects/" + projectId;
    }

    @PostMapping("/projects/{projectId}/configs/new/headers/add")
    public String addHeaderRowOnCreate(@PathVariable Long projectId,
                                        @ModelAttribute("configForm") ConfigForm form,
                                        Model model) {
        form.getHeaders().add(new HeaderEntryForm("", ""));
        model.addAttribute("projectId", projectId);
        return "configs/form";
    }

    @PostMapping("/projects/{projectId}/configs/new/headers/{index}/remove")
    public String removeHeaderRowOnCreate(@PathVariable Long projectId,
                                           @PathVariable int index,
                                           @ModelAttribute("configForm") ConfigForm form,
                                           Model model) {
        removeHeaderRow(form, index);
        model.addAttribute("projectId", projectId);
        return "configs/form";
    }

    @PostMapping("/configs/{id}/headers/add")
    public String addHeaderRowOnEdit(@PathVariable Long id,
                                      @ModelAttribute("configForm") ConfigForm form,
                                      Model model) {
        form.getHeaders().add(new HeaderEntryForm("", ""));
        model.addAttribute("projectId", configService.getById(id).getProject().getId());
        model.addAttribute("configId", id);
        return "configs/form";
    }

    @PostMapping("/configs/{id}/headers/{index}/remove")
    public String removeHeaderRowOnEdit(@PathVariable Long id,
                                         @PathVariable int index,
                                         @ModelAttribute("configForm") ConfigForm form,
                                         Model model) {
        removeHeaderRow(form, index);
        model.addAttribute("projectId", configService.getById(id).getProject().getId());
        model.addAttribute("configId", id);
        return "configs/form";
    }

    private static void removeHeaderRow(ConfigForm form, int index) {
        List<HeaderEntryForm> headers = form.getHeaders();
        if (index >= 0 && index < headers.size()) {
            headers.remove(index);
        }
    }

    private static Map<String, String> toHeaderMap(List<HeaderEntryForm> entries) {
        Map<String, String> result = new LinkedHashMap<>();
        if (entries != null) {
            for (HeaderEntryForm entry : entries) {
                if (entry.getKey() != null && !entry.getKey().isBlank()) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }
}
