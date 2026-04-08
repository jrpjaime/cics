import { Component, EventEmitter, Input, Output } from '@angular/core';
import { PaginatorState } from '../PaginatorState';

@Component({
  selector: 'app-paginatorui-component',
  imports: [],
  templateUrl: './paginatorui-component.html',
  styleUrl: './paginatorui-component.css',
})
export class PaginatorUiComponent {
  /** Recibe la instancia de la clase PaginatorState que creaste */
  @Input({ required: true }) instanciaPaginador!: PaginatorState;

  /** Emite el nuevo índice al componente padre para que haga la petición al servicio */
  @Output() cambioDePagina = new EventEmitter<number>();

  /**
   * Método interno para manejar el click.
   * Valida que no se emitan páginas fuera de rango.
   */
  cambiarPagina(nuevoIndice: number): void {
    if (nuevoIndice >= 0 && nuevoIndice < this.instanciaPaginador.totalDePaginas()) {
      this.cambioDePagina.emit(nuevoIndice);
    }
  }
}
