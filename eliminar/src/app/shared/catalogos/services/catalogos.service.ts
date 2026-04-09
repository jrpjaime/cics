import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { EPs } from '../../../global/endPoint';





@Injectable({
  providedIn: 'root'
})
export class CatalogosService {

  constructor(
    private httpClient: HttpClient,
    private router: Router
  ) { }


  public list(): Observable<any> {
    return this.httpClient.post<any>(environment.catalogosApiUrl + EPs.catalogo.list, null);
  }






}



