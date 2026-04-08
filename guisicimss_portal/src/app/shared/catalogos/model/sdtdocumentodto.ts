export class  SdtDocumentoDto  {

  idDocumento?: string;
  idSolicitudDevolucion?: string;
  refPath?: string;
  refTipoContenido?: string;
  refNombreDocumento?: string;
  numTamanio?: number;
  // No necesitas mapear refDocumento aquí porque lo recibes como blob en la respuesta
}
