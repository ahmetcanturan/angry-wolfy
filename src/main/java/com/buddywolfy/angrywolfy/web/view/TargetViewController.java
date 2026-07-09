package com.buddywolfy.angrywolfy.web.view;

import com.buddywolfy.angrywolfy.entity.Target;
import com.buddywolfy.angrywolfy.service.TargetService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class TargetViewController {

    private final TargetService targetService;

    public TargetViewController(TargetService targetService) {
        this.targetService = targetService;
    }

    @GetMapping("/projects/{projectId}/targets/new")
    public String newForm(@PathVariable Long projectId, Model model) {
        model.addAttribute("targetForm", new TargetForm());
        model.addAttribute("projectId", projectId);
        return "targets/form";
    }

    @PostMapping("/projects/{projectId}/targets")
    public String create(@PathVariable Long projectId,
                          @Valid @ModelAttribute("targetForm") TargetForm form,
                          BindingResult bindingResult,
                          Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("projectId", projectId);
            return "targets/form";
        }
        targetService.create(form.getName(), form.getDescription(), projectId, form.getPath(),
                form.getBaseUrlOverride(), form.getMethod(), form.getType(),
                toHeaderMap(form.getHeaders()), form.getBody(), form.getNotes());
        return "redirect:/projects/" + projectId;
    }

    @GetMapping("/targets/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Target target = targetService.getById(id);
        model.addAttribute("targetForm", TargetForm.fromEntity(target));
        model.addAttribute("projectId", target.getProject().getId());
        model.addAttribute("targetId", target.getId());
        return "targets/form";
    }

    @PostMapping("/targets/{id}")
    public String update(@PathVariable Long id,
                          @Valid @ModelAttribute("targetForm") TargetForm form,
                          BindingResult bindingResult,
                          Model model) {
        Long projectId = targetService.getById(id).getProject().getId();
        if (bindingResult.hasErrors()) {
            model.addAttribute("projectId", projectId);
            model.addAttribute("targetId", id);
            return "targets/form";
        }
        targetService.update(id, form.getName(), form.getDescription(), form.getPath(),
                form.getBaseUrlOverride(), form.getMethod(), form.getType(),
                toHeaderMap(form.getHeaders()), form.getBody(), form.getNotes());
        return "redirect:/projects/" + projectId;
    }

    @PostMapping("/targets/{id}/delete")
    public String delete(@PathVariable Long id) {
        Long projectId = targetService.getById(id).getProject().getId();
        targetService.delete(id);
        return "redirect:/projects/" + projectId;
    }

    @PostMapping("/projects/{projectId}/targets/new/headers/add")
    public String addHeaderRowOnCreate(@PathVariable Long projectId,
                                        @ModelAttribute("targetForm") TargetForm form,
                                        Model model) {
        form.getHeaders().add(new HeaderEntryForm("", ""));
        model.addAttribute("projectId", projectId);
        return "targets/form";
    }

    @PostMapping("/projects/{projectId}/targets/new/headers/{index}/remove")
    public String removeHeaderRowOnCreate(@PathVariable Long projectId,
                                           @PathVariable int index,
                                           @ModelAttribute("targetForm") TargetForm form,
                                           Model model) {
        removeHeaderRow(form, index);
        model.addAttribute("projectId", projectId);
        return "targets/form";
    }

    @PostMapping("/targets/{id}/headers/add")
    public String addHeaderRowOnEdit(@PathVariable Long id,
                                      @ModelAttribute("targetForm") TargetForm form,
                                      Model model) {
        form.getHeaders().add(new HeaderEntryForm("", ""));
        model.addAttribute("projectId", targetService.getById(id).getProject().getId());
        model.addAttribute("targetId", id);
        return "targets/form";
    }

    @PostMapping("/targets/{id}/headers/{index}/remove")
    public String removeHeaderRowOnEdit(@PathVariable Long id,
                                         @PathVariable int index,
                                         @ModelAttribute("targetForm") TargetForm form,
                                         Model model) {
        removeHeaderRow(form, index);
        model.addAttribute("projectId", targetService.getById(id).getProject().getId());
        model.addAttribute("targetId", id);
        return "targets/form";
    }

    private static void removeHeaderRow(TargetForm form, int index) {
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
