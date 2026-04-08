import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs'; 
import { ModalType } from '../model/modal-type.model';

@Injectable({
	providedIn: 'root'
})
export class ModalService {

	private listener$: Subject<any> = new Subject<void>();

	constructor() {
	 
		this.listener$ = new Subject<ModalType | undefined>();
	}

	get _listener(): Observable<ModalType | undefined> {
		return this.listener$.asObservable();
	}

	/**
	 * Muestra un diálogo modal configurable.
	 * @param type Define el número de botones: 'confirm' (dos botones) o 'alert' (un botón).
	 * @param style Define el estilo visual: 'info', 'warning', o 'error'.
	 * @param title El título del modal.
	 * @param message El mensaje del modal. Puede incluir HTML como <br>.
	 * @param callback Función que se ejecuta al cerrar, devuelve 'true' para confirmar, 'false' para rechazar.
	 * @param confirmText Texto del botón de confirmación (ej. 'Aceptar', 'Continuar').
	 * @param rejectText Texto del botón de rechazo (ej. 'Cancelar').
	 */
	showDialog(
		type: 'confirm' | 'alert' = 'confirm',
		style: 'info' | 'warning' | 'error' = 'info',
		title: string = "",
		message: string = "",
		callback: (result: boolean) => void = () => {},
		confirmText: string | Observable<string> = "Aceptar",
		rejectText: string = "Cancelar"
	) {
		const self = this;
		this.listener$.next({
			type: type,
			style: style,
			title: title,
			text: message,
			confirmText: confirmText,
			rejectText: rejectText,
			confirm: function () {
				self.listener$.next(undefined); // Oculta el modal
				callback(true);
			},
			reject: function () {
				self.listener$.next(undefined); // Oculta el modal
				callback(false);
			},
			dismiss: function () {
				self.listener$.next(undefined); // Oculta el modal
				// Por defecto, dismiss es como rechazar
				callback(false);
			}
		});
	}

	/**
     * Cierra y oculta cualquier modal que esté actualmente visible.
     */
    close(): void {
        // Emitir 'undefined' es la señal que usamos para ocultar el modal
        this.listener$.next(undefined);
    }
}
