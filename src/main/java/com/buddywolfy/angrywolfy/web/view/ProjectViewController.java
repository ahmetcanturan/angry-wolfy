package com.buddywolfy.angrywolfy.web.view;

import com.buddywolfy.angrywolfy.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/projects")
public class ProjectViewController {

    private final ProjectService projectService;

    public ProjectViewController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("projects", projectService.getAll());
        return "projects/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("project", new ProjectForm());
        return "projects/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("project") ProjectForm form,
                          BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "projects/form";
        }
        projectService.create(form.getName(), form.getDescription(), form.getRepositoryPath());
        return "redirect:/projects";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var project = projectService.getById(id);
        model.addAttribute("projectId", project.getId());
        model.addAttribute("project", new ProjectForm(
                project.getName(), project.getDescription(), project.getRepositoryPath()));
        return "projects/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                          @Valid @ModelAttribute("project") ProjectForm form,
                          BindingResult bindingResult,
                          Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("projectId", id);
            return "projects/form";
        }
        projectService.update(id, form.getName(), form.getDescription(), form.getRepositoryPath());
        return "redirect:/projects";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        projectService.delete(id);
        return "redirect:/projects";
    }
}
