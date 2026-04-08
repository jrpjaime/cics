import { computed, signal, Signal } from '@angular/core';

export class PaginatorState {
  // Signals de estado
  /** Índice de la página actual (basado en cero para el backend) */
  indicePaginaActual = signal(0);
  /** Cantidad total de páginas disponibles en el servidor */
  totalDePaginas = signal(0);
  /** Número total de registros encontrados en la base de datos */
  totalDeRegistros = signal(0);
  /** Cantidad de elementos a mostrar por página */
  tamanoPagina = signal(5);



  /**
 * Inicializa una nueva instancia para el control de la paginación.
 *
 * @param tamanoDePaginaInicial Define cuántos registros se mostrarán por cada página.
 * Por defecto, el sistema asume 5 registros si no se especifica otro valor.
 */
constructor(tamanoDePaginaInicial: number = 5) {
  // Asignamos el valor inicial al Signal que controla el límite de registros por consulta.
  this.tamanoPagina.set(tamanoDePaginaInicial);
}




  /**
   * Genera dinámicamente el arreglo de números de página (máximo 5)
   * centrados alrededor de la página actual para el control visual.
   */
  arregloDePaginas = computed(() => {
    const total = this.totalDePaginas();
    const actual = this.indicePaginaActual() + 1;
    if (total <= 1) return [];

    let inicio = Math.max(1, actual - 2);
    let fin = Math.min(total, inicio + 4);

    // Ajuste para asegurar que siempre se vean 5 números si existen suficientes páginas
    if (fin - inicio < 4) {
      inicio = Math.max(1, fin - 4);
    }

    const paginasVisibles = [];
    for (let i = inicio; i <= fin; i++) {
      paginasVisibles.push(i);
    }
    return paginasVisibles;
  });



  /**
   * Actualiza los signals de estado basándose en la respuesta Page del backend.
   * @param respuestaServidor Objeto que contiene los metadatos de paginación (number, totalPages, totalElements).
   */
  actualizarEstado(respuestaServidor: any): void {
    if (respuestaServidor) {
      this.indicePaginaActual.set(respuestaServidor.number || 0);
      this.totalDePaginas.set(respuestaServidor.totalPages || 0);
      this.totalDeRegistros.set(respuestaServidor.totalElements || 0);
    }
  }

  /** Restablece el paginador a su estado inicial. */
  limpiar(): void {
    this.indicePaginaActual.set(0);
    this.totalDePaginas.set(0);
    this.totalDeRegistros.set(0);
  }
}
