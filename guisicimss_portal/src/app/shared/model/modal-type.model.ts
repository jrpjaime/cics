import { Observable } from "rxjs";

export interface ModalType {

    type : string;
    title : string;
    text : string;
    imgSrc? : string;
    confirm? : Function;
    reject? : Function;
    dismiss? : Function | null | undefined;
    alerta? : boolean;

    style?: 'info' | 'warning' | 'error';  
    confirmText?: string | Observable<string>;  // El texto para el botón de confirmar
    rejectText?: string;                  // El texto para el botón de cancelar

        
   

 }
