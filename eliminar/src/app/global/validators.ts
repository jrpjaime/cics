import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

// Validador personalizado para comparar las fechas de inicio y fin
export function fechaInicioMenorOigualFechaFin(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const fecInicio = control.get('fecInicio')?.value;
    const fecFin = control.get('fecFin')?.value;

    // Verificar que fecInicio y fecFin son fechas válidas
    if (fecInicio && fecFin && new Date(fecInicio) > new Date(fecFin)) {
      return { 'fechaInvalid': 'La fecha inicial debe ser menor o igual a la fecha de fin' };
    }

    return null;  // Si no hay error, retorna null
  };
}
