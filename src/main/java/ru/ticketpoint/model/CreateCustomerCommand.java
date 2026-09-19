package ru.ticketpoint.model;

public record CreateCustomerCommand(String name) {
    public CreateCustomerCommand {
        name = Validation.text(name, "Имя", 120);
    }
}
