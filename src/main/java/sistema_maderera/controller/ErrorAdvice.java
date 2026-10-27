package sistema_maderera.controller;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import sistema_maderera.exception.RecursoNoEncontradoException;

@ControllerAdvice
public class ErrorAdvice {
    @ExceptionHandler(RecursoNoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String noEncontrado(RecursoNoEncontradoException e, Model model) {
        model.addAttribute("detalle", e.getMessage());
        return "error/404";
    }
}
