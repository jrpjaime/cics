import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AdminService {

  private readonly API_URL = `${environment.seguridadApiUrl}/v1/admin`;

  constructor(private http: HttpClient) {}

  /**
   * 1. LISTAR USUARIOS CON FILTROS Y PAGINACIÓN
   * @param page Índice de página (0...)
   * @param size Cantidad de registros por página
   * @param filtros Objeto con userApi, userMain y rol
   */
  listarUsuarios(page: number, size: number, filtros: any = {}): Observable<any> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    // Añadir filtros opcionales si tienen valor
    if (filtros.userApi) params = params.set('userApi', filtros.userApi);
    if (filtros.userMain) params = params.set('userMain', filtros.userMain);
    if (filtros.rol) params = params.set('rol', filtros.rol);

    return this.http.get<any>(`${this.API_URL}/usuarios`, { params });
  }

  /**
   * 2. OBTENER DETALLE DE USUARIO POR ID
   * Recupera los datos de MSCC_USUARIO_CICS y su lista de MSCC_PERMISO_CICS
   */
  obtenerUsuarioPorId(id: number): Observable<any> {
    const payload = { idUsuarioCics: id };
    // Enviamos el ID dentro de un objeto JSON
    return this.http.post<any>(`${this.API_URL}/usuarios/detalle`, payload);
  }
  /**
   * 3. REGISTRAR NUEVO USUARIO
   * Crea el registro en MSCC_USUARIO_CICS y sus permisos iniciales
   */
  registrarUsuario(datos: any): Observable<any> {
    return this.http.post<any>(`${this.API_URL}/usuarios`, datos);
  }

  /**
   * 4. ACTUALIZAR USUARIO EXISTENTE
   * Actualiza datos maestros y gestiona altas/bajas de permisos (programas)
   */
  actualizarUsuario(datos: any): Observable<any> {
    // Usamos PUT para seguir el estándar de actualización
    return this.http.put<any>(`${this.API_URL}/usuarios`, datos);
  }

  /**
   * 5. ASIGNAR UN PERMISO INDIVIDUAL
   * (Opcional, si prefieres asignar programas uno por uno)
   */
  asignarPermiso(datos: any): Observable<any> {
    return this.http.post<any>(`${this.API_URL}/permisos`, datos);
  }
}
