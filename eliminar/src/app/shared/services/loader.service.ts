import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class LoaderService {
  private _loading = new BehaviorSubject<boolean>(false);
  public readonly loader$ = this._loading.asObservable();
  private requestCount = 0; // Importante para manejar múltiples peticiones

  constructor() { }

  // Método llamado por el Interceptor para mostrar el loader
  show() {
    this.requestCount++;
    if (this.requestCount === 1) { // Solo actualiza si es la primera petición pendiente
      this._loading.next(true);
    }
  }

  // Método llamado por el Interceptor para ocultar el loader
  hide() {
    this.requestCount--;
    if (this.requestCount <= 0) { // Oculta el loader solo si no hay peticiones pendientes
      this.requestCount = 0; // Asegura que no sea negativo
      this._loading.next(false);
    }
  }

 
}